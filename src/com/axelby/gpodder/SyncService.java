package com.axelby.gpodder;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.Service;
import android.content.AbstractThreadedSyncAdapter;
import android.content.ContentProviderClient;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SyncResult;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.os.IBinder;

import com.axelby.gpodder.Client.Changes;

public class SyncService extends Service {
    private static final Object _syncAdapterLock = new Object();
    private static SyncAdapter _syncAdapter = null;

    @Override
    public void onCreate() {
        synchronized (_syncAdapterLock) {
            if (_syncAdapter == null) {
                _syncAdapter = new SyncAdapter(getApplicationContext(), true);
            }
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return _syncAdapter.getSyncAdapterBinder();
    }

	private static class SyncAdapter extends AbstractThreadedSyncAdapter {
	
		private Context _context;
	
		public SyncAdapter(Context context, boolean autoInitialize) {
	        super(context, autoInitialize);
	        _context = context;
	    }
	
		@Override
		public void onPerformSync(Account account, Bundle extras, String authority,
				ContentProviderClient provider, SyncResult syncResult) {
			AccountManager accountManager = AccountManager.get(_context);
			Client client = new Client(_context, account.name, accountManager.getPassword(account));
			if (!client.authenticate())
				return;

			SharedPreferences gpodderPrefs = getContext().getSharedPreferences("gpodder", MODE_PRIVATE);
			int lastTimestamp = gpodderPrefs.getInt("lastTimestamp", 0);
			
			// send diffs to gpodder
			client.syncDiffs();

			// get the changes since the last time we updated
			Client.Changes changes = client.getSubscriptionChanges(lastTimestamp);
			updateSubscriptions(changes);

			// remember when we last updated
			SharedPreferences.Editor gpodderPrefsEditor = gpodderPrefs.edit();
			gpodderPrefsEditor.putInt("lastTimestamp", changes.timestamp);
			gpodderPrefsEditor.commit();

			// if there are changes, broadcast to all interested apps
			if (!changes.isEmpty()) {
				Intent intent = new Intent("com.axelby.gpodder.SUBSCRIPTION_UPDATE");
				intent.putExtra("com.axelby.gpodder.SUBSCRIPTION_ADDED", changes.added.toArray(new String[] { }));
				intent.putExtra("com.axelby.gpodder.SUBSCRIPTION_REMOVED", changes.removed.toArray(new String[] { }));
				_context.sendBroadcast(intent);
			}

		}

		private void updateSubscriptions(Changes changes) {
			SQLiteDatabase db = new DBAdapter(_context).getWritableDatabase();

			for (String addedUrl : changes.added)
				db.execSQL("INSERT INTO subscriptions(url) VALUES(?)", new Object[] { addedUrl });

			for (String removedUrl : changes.removed)
				db.execSQL("DELETE FROM subscriptions WHERE url = ?", new Object[] { removedUrl });
		}
	
	}
}