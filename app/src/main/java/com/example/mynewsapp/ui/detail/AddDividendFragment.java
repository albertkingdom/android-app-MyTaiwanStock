package com.example.mynewsapp.ui.detail;

import android.app.DatePickerDialog;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.DatePicker;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavHostController;
import androidx.navigation.fragment.NavHostFragment;

import com.example.mynewsapp.MyApplication;
import com.example.mynewsapp.R;
import com.example.mynewsapp.databinding.FragmentAddNewDividendBinding;
import com.example.mynewsapp.db.CashDividend;
import com.example.mynewsapp.db.StockDividend;
import com.example.mynewsapp.repository.NewsRepository;

import java.util.Calendar;
import java.util.Date;

import timber.log.Timber;

public class AddDividendFragment extends Fragment {
    private FragmentAddNewDividendBinding binding;
    private AddDividendViewModel viewModel;
    private String stockNo;
    private Long dateSelected = null;

    public AddDividendFragment() {
    }
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentAddNewDividendBinding.inflate(inflater);
        setHasOptionsMenu(true);
        NewsRepository repository = ((MyApplication) getActivity().getApplication()).getRepository();
        viewModel = new AddDividendViewModel(repository);

        stockNo = getArguments().getString("stockNo");
        binding.stockNo.setText(stockNo);
        binding.date.getEditText().setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showDatePickerDialog();
            }
        });
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

    }


    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        inflater.inflate(R.menu.add_history_fragment_option_menu, menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.finishButton) {
                saveDividend();

                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void saveDividend() {
        Integer cashDividend = 0;
        Integer stockDividend = 0;
        EditText cashDividendText = binding.cashDividend.getEditText();
        EditText stockDividendText = binding.stockDividend.getEditText();
        Long selectedDate = dateSelected != null ? dateSelected: new Date().getTime();

        if(cashDividendText!=null) {
            String cashStr = cashDividendText.getText().toString();
            if (!cashStr.isEmpty()) {
                cashDividend = Integer.parseInt(cashStr);
            }
            Timber.d("cash %s", cashDividend);
        }
        if(stockDividendText!=null){
            String stockStr = stockDividendText.getText().toString();

            if (!stockStr.isEmpty()) {
                stockDividend = Integer.parseInt(stockStr);
            }
        }

        if(cashDividend>0) {
            CashDividend newCashD = new CashDividend(0, stockNo, cashDividend, selectedDate);
            viewModel.insertCashDividend(newCashD);
        }

        if(stockDividend>0) {
            StockDividend newStockD = new StockDividend(0, stockNo, stockDividend, selectedDate);
            viewModel.insertStockDividend(newStockD);
        }

        if(cashDividend == 0 && stockDividend == 0) {
            Timber.d("請輸入數字");
        } else {
            NavHostFragment.findNavController(this).popBackStack();
        }
    }

    void showDatePickerDialog() {
        Calendar calendar = Calendar.getInstance();
        Integer year = calendar.get(Calendar.YEAR);
        Integer month = calendar.get(Calendar.MONTH);
        Integer day = calendar.get(Calendar.DAY_OF_MONTH);
        DatePickerDialog datePickerDialog = new DatePickerDialog(requireContext(), new DatePickerDialog.OnDateSetListener() {
            @Override
            public void onDateSet(DatePicker datePicker, int year, int month, int day) {
                binding.date.getEditText().setText(getString(R.string.add_stock_date_string, year, month+1, day));
                // set selected date in milliseconds
                calendar.set(year,month,day);
                dateSelected = calendar.getTimeInMillis();
            }
        }, year, month, day);
        datePickerDialog.show();
        datePickerDialog.getButton(DatePickerDialog.BUTTON_NEGATIVE).setTextColor(Color.BLACK);
        datePickerDialog.getButton(DatePickerDialog.BUTTON_POSITIVE).setTextColor(Color.BLACK);
    }
}