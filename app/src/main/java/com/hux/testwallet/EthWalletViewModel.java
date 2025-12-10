package com.hux.testwallet;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import org.web3j.crypto.WalletFile;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.http.HttpService;
import org.web3j.utils.Convert;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class EthWalletViewModel extends AndroidViewModel {
    private final WalletRepository walletRepository;
    private final ExecutorService executor = Executors.newFixedThreadPool(2);

    // UI State LiveData
    private final MutableLiveData<String> _walletAddress = new MutableLiveData<>();
    public final LiveData<String> walletAddress = _walletAddress;

    private final MutableLiveData<String> _walletBalance = new MutableLiveData<>();
    public final LiveData<String> walletBalance = _walletBalance;

    private final MutableLiveData<String> _toAddressBalance = new MutableLiveData<>();
    public final LiveData<String> toAddressBalance = _toAddressBalance;

    private final MutableLiveData<Event<String>> _transactionResult = new MutableLiveData<>();
    public final LiveData<Event<String>> transactionResult = _transactionResult;

    private final MutableLiveData<Boolean> _isLoading = new MutableLiveData<>(false);
    public final LiveData<Boolean> isLoading = _isLoading;

    private final MutableLiveData<String> _walletInfo = new MutableLiveData<>();
    public final LiveData<String> walletInfo = _walletInfo;

    private final MutableLiveData<String> _walletKeyInfo = new MutableLiveData<>();
    public final LiveData<String> walletKeyInfo = _walletKeyInfo;

    // Internal state
    private WalletFile mWalletFile;
    private Web3j mWeb3j;

    public EthWalletViewModel(@NonNull Application application) {
        super(application);
        this.walletRepository = new WalletRepository(application);
        this.mWeb3j = Web3j.build(new HttpService(Constants.ETHEREUM_SEPOLIA_URL));
    }

    public void loadWallet() {
        _isLoading.setValue(true);
        walletRepository.loadOrCreateWallet(new WalletRepository.WalletLoadCallback() {
            @Override
            public void onWalletLoaded(WalletFile walletFile) {
                mWalletFile = walletFile;
                String address = Constants.HEX_PREFIX + walletFile.getAddress();
                _walletAddress.postValue(address);
                updateWalletBalance();
                _isLoading.postValue(false);
            }
            @Override
            public void onError() {
                _isLoading.postValue(false);
                _transactionResult.postValue(new Event<>("Error: Could not load wallet."));
            }
        });
    }

    public void showWalletInfo(){
        if (mWalletFile == null) return;
        _walletInfo.postValue(EthWalletController.getInstance().exportKeyStore(mWalletFile));
    }

    public void showKeyInfo(){
        if (mWalletFile == null) return;;
        _walletKeyInfo.postValue(EthWalletController.getInstance().exportPrivateKey(mWalletFile));
    }

    public void updateWalletBalance() {
        if (mWalletFile == null) return;
        String address = Constants.HEX_PREFIX + mWalletFile.getAddress();
        executor.execute(() -> {
            try {
                BigDecimal ethBalance = fetchBalance(address);
                _walletBalance.postValue(formatBalance(ethBalance));
            } catch (IOException e) {
                _walletBalance.postValue("查询失败");
            }
        });
    }

    public void updateToAddressBalance(String toAddress) {
        if (toAddress == null || toAddress.isEmpty()) {
            _toAddressBalance.setValue("请输入地址");
            return;
        }
        executor.execute(() -> {
            try {
                BigDecimal ethBalance = fetchBalance(toAddress);
                _toAddressBalance.postValue(formatBalance(ethBalance));
            } catch (IOException e) {
                _toAddressBalance.postValue("查询失败");
            }
        });
    }

    public void sendEth(String toAddress, String amount) {
        if (mWalletFile == null || toAddress.isEmpty() || amount.isEmpty()) {
            _transactionResult.setValue(new Event<>("Error: Address or amount is empty."));
            return;
        }

        _isLoading.setValue(true);
        executor.execute(() -> {
            try {
                BigDecimal amountInEth = new BigDecimal(amount);
                String txHash = walletRepository.sendTransaction(mWeb3j, mWalletFile, toAddress, amountInEth);
                _transactionResult.postValue(new Event<>("Success! TxHash: " + txHash));
                // Wait a moment and then refresh balance
                Thread.sleep(5000);
                updateWalletBalance();
            } catch (Exception e) {
                _transactionResult.postValue(new Event<>("Error: " + e.getMessage()));
            } finally {
                _isLoading.postValue(false);
            }
        });
    }

    public void switchNetwork(String url) {
        mWeb3j = Web3j.build(new HttpService(url));
        updateWalletBalance();
    }

    private BigDecimal fetchBalance(String address) throws IOException {
        return Convert.fromWei(walletRepository.getBalance(mWeb3j, address).toString(), Convert.Unit.ETHER);
    }

    private String formatBalance(BigDecimal balance) {
        return balance.setScale(8, RoundingMode.FLOOR).toPlainString() + " ETH";
    }
}