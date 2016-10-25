package me.impy.aegis;

import android.app.FragmentManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.support.v7.widget.helper.ItemTouchHelper;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import com.yarolegovich.lovelydialog.LovelyTextInputDialog;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

import me.impy.aegis.crypto.CryptoUtils;
import me.impy.aegis.crypto.OTP;
import me.impy.aegis.db.Database;
import me.impy.aegis.helpers.SimpleItemTouchHelperCallback;

public class MainActivity extends AppCompatActivity  {

    static final int GET_KEYINFO = 1;
    static final int ADD_KEYINFO = 2;

    RecyclerView rvKeyProfiles;
    KeyProfileAdapter mKeyProfileAdapter;
    ArrayList<KeyProfile> mKeyProfiles;
    Database database;

    boolean nightMode = false;
    int count = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        SharedPreferences prefs = this.getSharedPreferences("me.impy.aegis", Context.MODE_PRIVATE);
        if(!prefs.getBoolean("passedIntro", false))
        {
            Intent intro = new Intent(this, IntroActivity.class);
            startActivity(intro);
        }

        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        if(sharedPreferences.getBoolean("pref_night_mode", false))
        {
            nightMode = true;
            setTheme(R.style.AppTheme_Dark_NoActionBar);
        } else
        {
            setPreferredTheme();
        }

        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent scannerActivity = new Intent(getApplicationContext(), ScannerActivity.class);
                startActivityForResult(scannerActivity, GET_KEYINFO);
            }
        });

        // demo
        char[] password = "test".toCharArray();
        database = Database.createInstance(getApplicationContext(), "keys.db", password);
        CryptoUtils.zero(password);

        mKeyProfiles = new ArrayList<>();

        rvKeyProfiles = (RecyclerView) findViewById(R.id.rvKeyProfiles);
        LinearLayoutManager mLayoutManager = new LinearLayoutManager(this);
        rvKeyProfiles.setLayoutManager(mLayoutManager);

        final Context context = this.getApplicationContext();
        /*rvKeyProfiles.addOnItemTouchListener(new RVHItemClickListener(this, new RVHItemClickListener.OnItemClickListener() {
            @Override
            public void onItemClick(View view, int position) {
                ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                ClipData clip = ClipData.newPlainText("text/plain", mKeyProfiles.get(position).Code);
                clipboard.setPrimaryClip(clip);

                Toast.makeText(context, "Code successfully copied to the clipboard", Toast.LENGTH_SHORT).show();
            }
        }));*/

        mKeyProfileAdapter = new KeyProfileAdapter(mKeyProfiles);
        //rvKeyProfiles.addItemDecoration(new RVHItemDividerDecoration(this, LinearLayoutManager.VERTICAL));

        //ItemTouchHelper.Callback callback = new RVHItemTouchHelperCallback(mKeyProfileAdapter, true, false, false);
        //ItemTouchHelper helper = new ItemTouchHelper(callback);
        //helper.attachToRecyclerView(rvKeyProfiles);

        ItemTouchHelper.Callback callback = new SimpleItemTouchHelperCallback(mKeyProfileAdapter);
        ItemTouchHelper touchHelper = new ItemTouchHelper(callback);
        touchHelper.attachToRecyclerView(rvKeyProfiles);

        rvKeyProfiles.setAdapter(mKeyProfileAdapter);
        Comparator<KeyProfile> comparator = new Comparator<KeyProfile>() {
            @Override
            public int compare(KeyProfile keyProfile, KeyProfile t1) {
                return keyProfile.Order - t1.Order;
            }
        };
        Collections.sort(mKeyProfiles, comparator);
        
        try {
            for (KeyProfile profile : database.getKeys()) {
                mKeyProfiles.add(profile);
            }
            mKeyProfileAdapter.notifyDataSetChanged();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        // Check which request we're responding to
        if (requestCode == GET_KEYINFO) {
            // Make sure the request was successful
            if (resultCode == RESULT_OK) {
                final KeyProfile keyProfile = (KeyProfile)data.getSerializableExtra("KeyProfile");

                Intent intent = new Intent(this, AddProfileActivity.class);
                intent.putExtra("KeyProfile", keyProfile);
                startActivityForResult(intent, ADD_KEYINFO);
                //TODO: do something with the result.
            }
        }
        else if (requestCode == ADD_KEYINFO) {
            // Make sure the request was successful
            if (resultCode == RESULT_OK) {
                final KeyProfile keyProfile = (KeyProfile)data.getSerializableExtra("KeyProfile");

                String otp;
                try {
                    otp = OTP.generateOTP(keyProfile.Info);
                } catch (Exception e) {
                    e.printStackTrace();
                    return;
                }

                keyProfile.Order = mKeyProfiles.size() + 1;
                keyProfile.Code = otp;
                mKeyProfiles.add(keyProfile);
                mKeyProfileAdapter.notifyDataSetChanged();

                try {
                    database.addKey(keyProfile);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private void adjustOrder(int startPosition)
    {
        Comparator<KeyProfile> comparator = new Comparator<KeyProfile>() {
            @Override
            public int compare(KeyProfile keyProfile, KeyProfile t1) {
                return keyProfile.Order - t1.Order;
            }
        };

        for(int i = startPosition; i < mKeyProfiles.size(); i++)
        {
            mKeyProfiles.get(i).Order = i + 1;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        setPreferredTheme();
    }

    @Override
    protected void onPause() {
        for(int i = 0; i < mKeyProfiles.size(); i++)
        {
            try {
                database.updateKey(mKeyProfiles.get(i));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        super.onPause();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {

            Intent preferencesActivity = new Intent(this, PreferencesActivity.class);
            startActivity(preferencesActivity);

            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void setPreferredTheme()
    {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        if(sharedPreferences.getBoolean("pref_night_mode", false))
        {
            if(!nightMode)
            {
                setTheme(R.style.AppTheme_Dark_NoActionBar);
                finish();

                startActivity(new Intent(this, this.getClass()));
            }
        } else
        {
            if(nightMode)
            {
                setTheme(R.style.AppTheme_Default_NoActionBar);
                finish();

                startActivity(new Intent(this, this.getClass()));
            }
        }
    }
}
