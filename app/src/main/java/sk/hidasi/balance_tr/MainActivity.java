package sk.hidasi.balance_tr;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        return BalanceWidgetHelper.onOptionsItemSelected(this, item);
    }

    public void onTutorialVideo(View view) {
        final Intent webIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://youtu.be/IouBtavQ9w4"));
        startActivity(webIntent);
    }
}
