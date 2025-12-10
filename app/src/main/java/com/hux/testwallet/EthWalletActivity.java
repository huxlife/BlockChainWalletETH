package com.hux.testwallet;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.liar.testwallet.R;

public class EthWalletActivity extends AppCompatActivity {

    private EditText mWalletAddressText;
    private TextView mWalletBalanceText;
    private EditText mToAddressEdit;
    private TextView mToAddressBalanceText;
    private EditText mAmountEdit;
    private TextView mNetworkTitleText;
    private Button mSendButton;
    private ProgressBar mProgressBar;
    //钱包信息
    private EditText mKeyStoreEdit;
    //钱包私钥
    private EditText mPrivateKeyEdit;
    private EthWalletViewModel viewModel;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ethereum_wallet);

        initUi();

        // Use AndroidViewModelFactory to pass Application context to ViewModel
        ViewModelProvider.AndroidViewModelFactory factory = ViewModelProvider.AndroidViewModelFactory.getInstance(getApplication());
        viewModel = new ViewModelProvider(this, (ViewModelProvider.Factory) factory).get(EthWalletViewModel.class);

        setupObservers();
        setupClickListeners();

        // Trigger the initial data load
        viewModel.loadWallet();
    }

    private void initUi() {
        mWalletAddressText = findViewById(R.id.address);
        mWalletBalanceText = findViewById(R.id.balance);
        mToAddressEdit = findViewById(R.id.to_address);
        mAmountEdit = findViewById(R.id.amount);
        mNetworkTitleText = findViewById(R.id.network_title);
        mToAddressBalanceText = findViewById(R.id.to_address_balance);
        mSendButton = findViewById(R.id.btn_send);
        mProgressBar = findViewById(R.id.progress_bar);
        mKeyStoreEdit = findViewById(R.id.key_store);
        mPrivateKeyEdit = findViewById(R.id.private_key);
        mToAddressEdit.setText(Constants.LIA_ADDRESS);
    }

    private void setupObservers() {
        viewModel.walletAddress.observe(this, address -> mWalletAddressText.setText(address));
        viewModel.walletBalance.observe(this, balance -> mWalletBalanceText.setText(balance));
        viewModel.toAddressBalance.observe(this, balance -> mToAddressBalanceText.setText(balance));
        viewModel.walletInfo.observe(this, info -> mKeyStoreEdit.setText(info));
        viewModel.walletKeyInfo.observe(this, info -> mPrivateKeyEdit.setText(info));

        viewModel.isLoading.observe(this, isLoading -> {
            mProgressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);
            mSendButton.setEnabled(!isLoading);
        });

        viewModel.transactionResult.observe(this, event -> {
            String message = event.getContentIfNotHandled();
            if (message != null) {
                Toast.makeText(this, message, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void setupClickListeners() {
        mSendButton.setOnClickListener(v -> {
            String to = mToAddressEdit.getText().toString();
            String amount = mAmountEdit.getText().toString();
            viewModel.sendEth(to, amount);
        });

        findViewById(R.id.btn_refresh_balance).setOnClickListener(v -> viewModel.updateWalletBalance());
        findViewById(R.id.btn_refresh_to_balance).setOnClickListener(v -> viewModel.updateToAddressBalance(mToAddressEdit.getText().toString()));
        findViewById(R.id.showKeyStore).setOnClickListener(v -> viewModel.showWalletInfo());
        findViewById(R.id.showPrivateKey).setOnClickListener(v -> viewModel.showKeyInfo());
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.eth_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.switch_mainnet) {
            mNetworkTitleText.setText("Mainnet");
            viewModel.switchNetwork(Constants.ETHEREUM_MAINNET_URL);
            return true;
        } else if (item.getItemId() == R.id.switch_sepolia) {
            mNetworkTitleText.setText("Sepolia Testnet");
            viewModel.switchNetwork(Constants.ETHEREUM_SEPOLIA_URL);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}