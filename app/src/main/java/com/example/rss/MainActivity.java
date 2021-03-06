package com.example.rss;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.PopupMenu;
import android.support.v7.widget.RecyclerView;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import com.example.rss.adapter.ClickListener;
import com.example.rss.adapter.RecyclerTouchListener;
import com.example.rss.adapter.SitesAdapter;
import com.example.rss.model.Site;
import com.example.rss.network.ApiAdapter;
import com.example.rss.ui.AddActivity;
import com.example.rss.ui.EmailActivity;
import com.example.rss.ui.UpdateActivity;

import java.io.IOException;
import java.util.ArrayList;

import butterknife.BindView;
import butterknife.ButterKnife;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {
    public static final int ADD_CODE = 100;
    public static final int UPDATE_CODE = 200;
    public static final int OK = 1;
    public static final String MAIL = "mail";

    @BindView(R.id.floatingActionButton)
    FloatingActionButton fab;
    @BindView(R.id.recyclerView)
    RecyclerView recyclerView;

    int positionClicked;
    private SitesAdapter adapter;
    private ArrayList<Site> sites;

    ProgressDialog progreso;
    //ApiService apiService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ButterKnife.bind(this);
        //fab = (FloatingActionButton) findViewById(R.id.floatingActionButton);
        fab.setOnClickListener(this);
        //Initialize RecyclerView
        //recyclerView = (RecyclerView) findViewById(R.id.recyclerView);
        adapter = new SitesAdapter();
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);
        //manage click
        recyclerView.addOnItemTouchListener(new RecyclerTouchListener(this, recyclerView, new ClickListener() {
            @Override
            public void onClick(View view, final int position) {
                showPopup(view, position);
            }
            @Override
            public void onLongClick(View view, int position) {
                Intent emailIntent = new Intent(getApplicationContext(), EmailActivity.class);
                emailIntent.putExtra(MAIL, adapter.getAt(position).getEmail());
                startActivity(emailIntent);
            }
        }));

        downloadSites();
    }

    private void downloadSites() {
        progreso = new ProgressDialog(this);
        progreso.setProgressStyle(ProgressDialog.STYLE_SPINNER);
        progreso.setMessage("Connecting . . .");
        progreso.setCancelable(false);
        progreso.show();

        Call<ArrayList<Site>> call = ApiAdapter.getInstance().getSites();
        call.enqueue(new Callback<ArrayList<Site>>() {
            @Override
            public void onResponse(Call<ArrayList<Site>> call, Response<ArrayList<Site>> response) {
                progreso.dismiss();
                if (response.isSuccessful()) {
                    sites = response.body();
                    adapter.setSites(response.body());
                    showMessage("Sites downloaded ok");
                } else {
                    StringBuilder message = new StringBuilder();
                    message.append("Download error: " + response.code());
                    if (response.body() != null)
                        message.append("\n" + response.body());
                    if (response.errorBody() != null)
                        try {
                            message.append("\n" + response.errorBody().string());
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    showMessage(message.toString());
                }
            }

            @Override
            public void onFailure(Call<ArrayList<Site>> call, Throwable t) {
                progreso.dismiss();
                if (t != null)
                    showMessage("Failure in the communication\n" + t.getMessage());
            }
        });
        progreso.setProgressStyle(ProgressDialog.STYLE_SPINNER);
        progreso.setMessage("Connecting . . .");
        progreso.setCancelable(false);
        progreso.show();
    }

    private void showMessage(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onClick(View view) {
        if (view == fab) {
            Intent i = new Intent(this, AddActivity.class);
            startActivityForResult(i, ADD_CODE);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        Site site = new Site();

        if (requestCode == ADD_CODE)
            if (resultCode == OK) {
                site.setId(data.getIntExtra("id", 1));
                site.setName(data.getStringExtra("name"));
                site.setLink(data.getStringExtra("link"));
                site.setEmail(data.getStringExtra("email"));
                adapter.add(site);
            }

        if (requestCode == UPDATE_CODE)
            if (resultCode == OK) {
                site.setId(data.getIntExtra("id", 1));
                site.setName(data.getStringExtra("name"));
                site.setLink(data.getStringExtra("link"));
                site.setEmail(data.getStringExtra("email"));
                adapter.modifyAt(site, positionClicked);
            }
    }

    private void showPopup(View v, final int position) {
        PopupMenu popup = new PopupMenu(this, v);
        // Inflate the menu from xml
        popup.getMenuInflater().inflate(R.menu.popup_change, popup.getMenu());
        // Setup menu item selection
        popup.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            public boolean onMenuItemClick(MenuItem item) {
                switch (item.getItemId()) {
                    case R.id.modify_site:
                        modify(adapter.getAt(position));
                        positionClicked = position;
                        return true;
                    case R.id.delete_site:
                        confirm(adapter.getAt(position).getId(), adapter.getAt(position).getName(), position);
                        return true;
                    default:
                        return false;
                }
            }
        });
        // Show the menu
        popup.show();
    }

    private void modify(Site s) {
        Intent i = new Intent(this, UpdateActivity.class);
        i.putExtra("site", s);
        startActivityForResult(i, UPDATE_CODE);
    }

    private void confirm(final int idSite, String name, final int position) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage(name + "\nDo you want to delete?")
                .setTitle("Delete")
                .setPositiveButton("Confirm", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        dialog.dismiss();
                        connection(idSite, position);
                    }
                })
                .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        dialog.dismiss();
                    }
                });
        builder.show();
    }

    private void connection(int idSite, final int position) {
        Call<ResponseBody> call = ApiAdapter.getInstance().deleteSite(position);
        progreso.setProgressStyle(ProgressDialog.STYLE_SPINNER);
        progreso.setMessage("Connecting . . .");
        progreso.setCancelable(false);
        progreso.show();
        call.enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                progreso.dismiss();
                if (response.isSuccessful()) {
                    adapter.removeAt(position);
                    showMessage("Site deleted OK");
                } else {
                    StringBuilder message = new StringBuilder();
                    message.append("Error deleting a site: " + response.code());
                    if (response.body() != null)
                        message.append("\n" + response.body());
                    if (response.errorBody() != null)
                        try {
                            message.append("\n" + response.errorBody().string());
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    showMessage(message.toString());
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                progreso.dismiss();
                if (t != null)
                    showMessage("Failure in the communication\n" + t.getMessage());
            }
        });


    }

}
