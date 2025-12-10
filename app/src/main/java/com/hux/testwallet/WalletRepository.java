package com.hux.testwallet;

import android.content.Context;
import android.content.ContextWrapper;
import android.util.Log;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.web3j.crypto.CipherException;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.ECKeyPair;
import org.web3j.crypto.Keys;
import org.web3j.crypto.RawTransaction;
import org.web3j.crypto.TransactionEncoder;
import org.web3j.crypto.Wallet;
import org.web3j.crypto.WalletFile;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.response.EthSendTransaction;
import org.web3j.utils.Convert;
import org.web3j.utils.Numeric;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.security.InvalidAlgorithmParameterException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Repository for managing wallet data. It abstracts the data source (file system, network).
 * This class replaces EthWalletController.
 */
public class WalletRepository {
    private static final String TAG = "WalletRepository";

    private final ContextWrapper contextWrapper;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public WalletRepository(ContextWrapper contextWrapper) {
        this.contextWrapper = contextWrapper;
    }

    public interface WalletLoadCallback {
        void onWalletLoaded(WalletFile walletFile);
        void onError();
    }

    public void loadOrCreateWallet(WalletLoadCallback callback) {
        executor.execute(() -> {
            File walletDir = contextWrapper.getDir("eth", Context.MODE_PRIVATE);
            if (walletDir.exists() && walletDir.listFiles() != null && walletDir.listFiles().length > 0) {
                // Load existing wallet
                Log.d(TAG, "Loading existing wallet from file.");
                try {
                    WalletFile wallet = objectMapper.readValue(walletDir.listFiles()[0], WalletFile.class);
                    callback.onWalletLoaded(wallet);
                } catch (IOException e) {
                    Log.e(TAG, "Failed to read wallet file", e);
                    callback.onError();
                }
            } else {
                // Create new wallet
                Log.d(TAG, "No wallet found, creating a new one.");
                try {
                    ECKeyPair ecKeyPair = Keys.createEcKeyPair();
                    WalletFile wallet = Wallet.createLight(Constants.PASSWORD, ecKeyPair);
                    String walletFileName = getWalletFileName(wallet);
                    File destination = new File(walletDir, walletFileName);
                    objectMapper.writeValue(destination, wallet);
                    callback.onWalletLoaded(wallet);
                } catch (NoSuchAlgorithmException | NoSuchProviderException | InvalidAlgorithmParameterException | CipherException | IOException e) {
                    Log.e(TAG, "Failed to create new wallet", e);
                    callback.onError();
                }
            }
        });
    }

    public BigInteger getBalance(Web3j web3j, String owner) throws IOException {
        return web3j.ethGetBalance(owner, DefaultBlockParameterName.LATEST).send().getBalance();
    }

    public String sendTransaction(Web3j web3j, WalletFile walletFile, String toAddress, BigDecimal amountInEth) throws IOException, CipherException {
        BigInteger transactionCount = web3j.ethGetTransactionCount(Constants.HEX_PREFIX + walletFile.getAddress(), DefaultBlockParameterName.LATEST).send().getTransactionCount();
        BigInteger gasPrice = web3j.ethGasPrice().send().getGasPrice();
        BigInteger gasLimit = new BigInteger("21000"); // Standard gas limit for ETH transfer
        BigInteger value = Convert.toWei(amountInEth, Convert.Unit.ETHER).toBigInteger();

        RawTransaction rawTransaction = RawTransaction.createEtherTransaction(transactionCount, gasPrice, gasLimit, toAddress, value);

        ECKeyPair ecKeyPair = Wallet.decrypt(Constants.PASSWORD, walletFile);
        Credentials credentials = Credentials.create(ecKeyPair);
        byte[] signedMessage = TransactionEncoder.signMessage(rawTransaction, credentials);
        String hexValue = Numeric.toHexString(signedMessage);

        EthSendTransaction ethSendTransaction = web3j.ethSendRawTransaction(hexValue).send();
        if (ethSendTransaction.hasError()) {
            throw new IOException("Error sending transaction: " + ethSendTransaction.getError().getMessage());
        }
        return ethSendTransaction.getTransactionHash();
    }

    private static String getWalletFileName(WalletFile walletFile) {
        SimpleDateFormat dateFormat = new SimpleDateFormat("'UTC--'yyyy-MM-dd'T'HH-mm-ss.SSS'--'");
        return dateFormat.format(new Date()) + walletFile.getAddress() + ".json";
    }
}