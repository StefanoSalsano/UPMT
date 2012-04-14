//#ifdef ANDROID
package com.and.gui.activity;

import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.util.Vector;

import org.zoolu.tools.Log;

import upmt.TunnelInfo;
import upmt.client.UPMTClient;
import upmt.client.application.manager.ApplicationManager;
import upmt.client.application.manager.ApplicationManagerListener;
import upmt.client.core.Socket;
import upmt.client.sip.SipSignalManager;
import upmt.os.Shell;
import android.Manifest.permission;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.IBinder;
import android.view.ContextMenu;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.and.gui.App;
import com.and.gui.GraphView;
import com.and.gui.NetMeterService;
import com.and.gui.R;
import com.and.gui.utils.ApplicationAdapter;

public class GUI extends Activity implements ApplicationManager {
	final private String TAG = "GUI";
	public static final String APP_TITLE = "APP_TITLE";
	// private List<ActivityManager.RunningServiceInfo> services;
	private static List<ActivityManager.RunningAppProcessInfo> services;
	private static ActivityManager activityManager;
	private static List<ApplicationInfo> list;
	private static PackageManager pm;
	private ListView listView;
	private ArrayList<App> appList;
	private ArrayList<App> priorityAppList;
	private static NotificationManager mNM;
	private static Notification notification;
	private static CharSequence text = null;
	private static PendingIntent contentIntent = null;
	private static String title = null;

	private static final int LAUNCH = 1;

	private static ApplicationManagerListener listener;
	private static String DefaultPolicy;

	/** socketID -> appName */
	private Hashtable<String, String> appForSocket = new Hashtable<String, String>();

	/**
	 * number of connected Anchor Nodes
	 */
	private int numOfConnectedAN;
	private String defaultAN;
	private String vipaFix;
	private static Vector<String> ifList;

	private static final int EXIT = 0;
	private static final int REFRESH = 1;
	private static final int SETTINGS = 2;
	private static final boolean ONLY_GUI = false;

	private NetMeterService mService;
	private Vector<TextView> mStatsFields;
	private Vector<TextView> mInfoFields;
	private GraphView mGraph;

	/**
	 * Service connection callback object used to establish communication with
	 * the service after binding to it.
	 */
	private ServiceConnection mConnection = new ServiceConnection() {
		public void onServiceConnected(ComponentName className, IBinder service) {

			mService = ((NetMeterService.NetMeterBinder) service).getService();
			android.util.Log.i(TAG, "service connected");
			mService.setDisplay(mStatsFields, mInfoFields, mGraph);
		}

		public void onServiceDisconnected(ComponentName className) {
			mService = null;
			android.util.Log.i(TAG,
					"service disconnected - should never happen");
		}
	};

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);

		mStatsFields = new Vector<TextView>();
		mInfoFields = new Vector<TextView>();

		listView = (ListView)findViewById(R.id.appList);
		mGraph = (GraphView)findViewById(R.id.graph);

		mNM = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
		postNotification();


		startService(new Intent(this.getApplicationContext(), NetMeterService.class));


		if(!ONLY_GUI){
			UPMTClient.start(null, getApplicationContext());
		}

		createTable();
	}

	@Override
	protected void onStart() {
		super.onStart();

		postNotification();

		if (!ONLY_GUI)
			refreshApp();
	}

	/**
	 * Framework method called when activity becomes the foreground activity.
	 * 
	 * onResume/onPause implement the most narrow window of activity life-cycle
	 * during which the activity is in focus and foreground.
	 */
	@Override
	public void onResume() {
		super.onResume();
		bindService(new Intent(this, NetMeterService.class), mConnection,
				Context.BIND_AUTO_CREATE);
	}

	/**
	 * Framework method called when activity looses foreground position
	 */
	@Override
	public void onPause() {
		super.onPause();
		unbindService(mConnection);
	}

	@Override
	public void onBackPressed() {
		moveTaskToBack(false);
	}

	private void refreshApp() {
		boolean isRun;

		appList = new ArrayList<App>();
		appList.clear();
		priorityAppList = new ArrayList<App>();
		priorityAppList.clear();

		pm = getPackageManager();
		list = pm.getInstalledApplications(0);

		//Collections.sort(list, new ApplicationInfo.DisplayNameComparator(pm));

		ApplicationInfo content;
		activityManager = (ActivityManager) this.getSystemService(ACTIVITY_SERVICE);
		// services = actvityManager.getRunningServices(list.size());
		services = activityManager.getRunningAppProcesses();

		String currPolicy, storPolicy, currentInterf, appName, pack;

		listener = UPMTClient.instance();

		DefaultPolicy = listener.getDefaultPolicy();

		for (int index = 0; index < list.size(); index++) {
			content = list.get(index);
			appName = (String) pm.getApplicationLabel(content);
			pack = (String) content.packageName;

			//System.out.println(pack);

			//if (appName.equals("Browser")) {
			//	System.out.println("Browser -> ok");
			//}

			if (pm.checkPermission(permission.INTERNET, pack) == PackageManager.PERMISSION_GRANTED || pack.contains("term")) {
				isRun = isRunningService(content);
				storPolicy = listener.getStoredPolicy(pack);

				try {
					currPolicy = listener.getCurrentPolicy(pack);
				} catch (NullPointerException ex) {
					currPolicy = null;
				}
				if (currPolicy == null && storPolicy == null)
					currPolicy = DefaultPolicy;

				//pcerqua
				if(currPolicy == null)
					currPolicy = storPolicy;
				//System.out.println("\t\t\t\t STORED : " + storPolicy);
				//System.out.println("\t\t\t\t Current : " + currPolicy);

				currentInterf = listener.getCurrentInterf(pack);

				if (appName.equals("Browser") || 
						appName.equals("Market") || 
						appName.equals("Skype") || 
						appName.equals("Twitter") |
						appName.equals("YouTube"))
					priorityAppList.add(0,new App(appName, pack, currPolicy, storPolicy,currentInterf, isRun, pm.getApplicationIcon(content)));

				else {
					if (isRun == false) {
						appList.add(new App(appName, pack, currPolicy,storPolicy, currentInterf, isRun, pm.getApplicationIcon(content)));
					} else {
						appList.add(0,new App(appName, pack, currPolicy, storPolicy,currentInterf, isRun, pm.getApplicationIcon(content)));
					}
				}

				if(index == 0){
					App app = new App("Media Server", "/system/bin/mediaserver", currPolicy, storPolicy, currentInterf, isRun, pm.getApplicationIcon(content));
					priorityAppList.add(app);
				}

			}

		}
		appList.addAll(0, priorityAppList);

		listView.setAdapter(new ApplicationAdapter(this, R.layout.app_row_item,	new ArrayList<App>()));

		new BackgroundWorker().execute(appList);
	}

	public static App singleApp(String singleAppName) {
		boolean isRun;

		ApplicationInfo content;
		services = activityManager.getRunningAppProcesses();

		String currPolicy, storPolicy, currentInterf, appName, pack;

		for (int index = 0; index < list.size(); index++) {
			content = list.get(index);
			appName = (String) pm.getApplicationLabel(content);

			if (!appName.equals(singleAppName))
				continue;

			pack = (String) content.packageName;
			isRun = isRunningService(content);
			storPolicy = listener.getStoredPolicy(pack);
			try {
				currPolicy = listener.getCurrentPolicy(pack);
			} catch (NullPointerException ex) {
				currPolicy = null;
			}

			if (currPolicy == null && storPolicy == null)
				currPolicy = DefaultPolicy;

			currentInterf = listener.getCurrentInterf(pack);

			return new App(appName, pack, currPolicy, storPolicy,
					currentInterf, isRun, pm.getApplicationIcon(content));
		}
		return null;
	}

	private static Boolean isRunningService(ApplicationInfo content) {
		for (int i = 0; i < services.size(); i++) {
			if (content.packageName.equals(services.get(i).processName)) {
				return true;
			}
		}
		return false;
	}

	private class BackgroundWorker extends AsyncTask<ArrayList<App>, App, Void> {

		@SuppressWarnings("unchecked")
		@Override
		protected void onPreExecute() {
			// Prima di iniziare a inserire gli elementi svuotiamo l'adapter
			((ArrayAdapter<App>) listView.getAdapter()).clear();
			super.onPreExecute();
		}

		@Override
		protected Void doInBackground(ArrayList<App>... params) {
			// Qui dentro si possono mettere le operazioni che potrebbero
			// rallentare il caricamento della listview

			ArrayList<App> listApp = params[0];

			for (int i = 0; i < listApp.size(); i++) {
				// Pubblichiamo il progresso
				publishProgress(listApp.get(i));
			}

			return null;
		}

		@SuppressWarnings("unchecked")
		@Override
		protected void onProgressUpdate(App... values) {
			// Aggiungiamo il progresso pubblicato all'adapter
			((ArrayAdapter<App>) listView.getAdapter()).add(values[0]);
			super.onProgressUpdate(values);
		}

		@Override
		protected void onPostExecute(Void result) {
			super.onPostExecute(result);
			listView.setOnItemClickListener(new OnItemClickListener() {

				@Override
				public void onItemClick(AdapterView<?> parent, View view,
						int position, long id) {
					App app = (App) parent.getItemAtPosition(position);
					//System.out.println(app.toString());
					// Intent intent = new Intent(parent.getContext(),
					// PreferencesEditor.class);
					Intent intent = new Intent(parent.getContext(),
							Policy.class);
					intent.putExtra(getString(R.string.CURRENT_INTERFACE_TAG),
							app.getCurrentInterf());
					intent.putExtra(getString(R.string.CURRENT_POLICY_TAG),
							app.getCurrPolicy());
					intent.putExtra(getString(R.string.STORED_POLICY_TAG),
							app.getStorPolicy());
					intent.putExtra(getString(R.string.APP_NAME_TAG),
							app.getName());
					intent.putExtra(getString(R.string.PACK_NAME_TAG),
							app.getPack());
					intent.putExtra("iflist", ifList);

					startActivity(intent);
				}
			});
			registerForContextMenu(listView);
		}
	}

	@Override
	public void startListen(ApplicationManagerListener listener) {
		GUI.listener = listener;
		vipaFix = listener.getVipaFix();
		numOfConnectedAN = listener.getNumOfConnectedAN();
		defaultAN = listener.getDefaultAN();
	}

	@Override
	public void addApp(String appName) {
		printLog("ADDING APP " + appName, Log.LEVEL_HIGH);
		// TODO ???
	}

	@Override
	public void removeApp(String appName) {
		printLog("removing " + appName, Log.LEVEL_HIGH);
		// TODO ???
	}

	@Override
	public void addSocket(String appName, Socket socket) {
		// TODO ???
		appForSocket.put(socket.id(), appName);
	}

	@Override
	public void rmvSocket(Socket socket) {
		// TODO ???
	}

	@Override
	public void changeDefaultAN(String ANAddress) {
		defaultAN = ANAddress;
	}

	@Override
	public void setConnectedAN(int n) {
		numOfConnectedAN = n;
	}

	@Override
	public void addInterface(String ifName) {
		System.out.println("addInterface(String ifName): " + ifName);
		ifList.add(ifName);
		postNotification();
	}

	@Override
	public void removeInterface(String ifName) {
		ifList.remove(ifName);
		postNotification();
	}

	@Override
	public void onPolicyCheck() {
		// TODO Auto-generated method stub

	}

	@Override
	public void startWorking() {
		getInfo();
		ifList = listener.getInterfacesList();
		for (int i = 0; i < ifList.size(); i++)
			postNotification();
	}

	public void getInfo() {
		vipaFix = listener.getVipaFix();
		numOfConnectedAN = listener.getNumOfConnectedAN();
		defaultAN = listener.getDefaultAN();
	}

	public static void onApplyPolicy(String app, String policy) {
		listener.setCurrentPolicy(app, policy);
		Toast.makeText(UPMTClient.getContext(), "applied policy",
				Toast.LENGTH_SHORT).show();
	}

	public static void onSavePolicy(String app, String newPolicy) {
		String oldPolicy = listener.getStoredPolicy(app);
		String toastMsg = "Policy already exist";
		if (oldPolicy == null) {
			listener.cfgPolicyAdding(app, newPolicy);
			toastMsg = "Policy saved";
		} else if (!newPolicy.equals(oldPolicy)) {
			listener.cfgPolicyEdit(app, newPolicy);
			toastMsg = "policy edited";
		}
		Toast.makeText(UPMTClient.getContext(), toastMsg, Toast.LENGTH_SHORT)
		.show();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		super.onCreateOptionsMenu(menu);
		if (!ONLY_GUI) {
			menu.add(0, REFRESH, Menu.NONE, R.string.refresh);
			menu.add(0, SETTINGS, Menu.NONE, R.string.settings);
		}
		menu.add(0, EXIT, Menu.NONE, R.string.exit);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		super.onOptionsItemSelected(item);
		switch (item.getItemId()) {
		case (EXIT): {
			Toast.makeText(UPMTClient.getContext(), "exiting...", Toast.LENGTH_SHORT).show();
			mNM.cancel(R.string.iconized);
			stopService(new Intent(this, NetMeterService.class));
			if (!ONLY_GUI)
				listener.stop();
			Shell.executeRootCommand(new String[]{"upmtconf", "-f" , "all"});		
			System.out.println("Exiting UPMT<<<<<<<<<<<<<<<<<<<<<<<<<");
			finish();
			return true;
		}
		case (REFRESH): {
			refreshApp();
			return true;
		}
		case (SETTINGS): {
			getInfo();
			Toast.makeText(
					getApplicationContext(),
					getString(R.string.VIPA) + vipaFix + ";\n"
							+ getString(R.string.CONNECTED_ANCHOR_NODES)
							+ numOfConnectedAN + ";\n"
							+ getString(R.string.DEFAULT_ANCHOR_NODE)
							+ defaultAN, Toast.LENGTH_LONG).show();


			Intent intent = new Intent(getApplicationContext(), Setting.class);

			Hashtable<String, Integer> AssociatedANListCopy = ((UPMTClient)listener).getAssociatedANList();
			String ANs = "";
			for(String s : AssociatedANListCopy.keySet()){
				ANs += s + ":" + AssociatedANListCopy.get(s) + ";";
			}

			Hashtable<String, TunnelInfo> ANStatusesCopy = ((UPMTClient)listener).getANStatuses();
			String ANstatuses = "";
			for(String s : ANStatusesCopy.keySet()){
				ANstatuses += s + ":" + (ANStatusesCopy.get(s).getStatus()==1 ? "c" : "d") + ";";
			}

			String tunnelDetails = "";

			for(String s : ANStatusesCopy.keySet()){
				TunnelInfo ti = ANStatusesCopy.get(s);
				tunnelDetails += s + ": " + (ti.getStatus()==1 ? "ok" : "disconnected") + "\nDelay: " + ti.getEWMA_Delay() + " ms"
						+ "\nLoss: " + ti.getEWMA_Loss()+ "%\n;";
			}

			intent.putExtra("ANs", ANs);
			intent.putExtra("ANsStatuses", ANstatuses);
			intent.putExtra("vipaFix", ((UPMTClient)listener).getVipaFix());
			intent.putExtra("maxANs", ((UPMTClient)listener).getMaxANNumber());
			intent.putExtra("defaultAN", ((UPMTClient)listener).getDefaultAN());
			intent.putExtra("tunnelDetails", tunnelDetails);

			startActivity(intent);

			return true;
		}
		}
		return false;
	}

	@Override
	public void onCreateContextMenu(ContextMenu menu, View v,
			ContextMenu.ContextMenuInfo menuInfo) {
		super.onCreateContextMenu(menu, v, menuInfo);
		switch (v.getId()) {
		case (R.id.appList): {
			// AdapterContextMenuInfo info = (AdapterContextMenuInfo) menuInfo;
			menu.add(0, LAUNCH, Menu.NONE, R.string.launch);
			break;
		}
		}
	}

	@Override
	public boolean onContextItemSelected(MenuItem item) {
		super.onContextItemSelected(item);
		AdapterView.AdapterContextMenuInfo menuInfo = (AdapterView.AdapterContextMenuInfo) item
				.getMenuInfo();
		int index = menuInfo.position;
		int itemId = item.getItemId();
		switch (itemId) {
		case (LAUNCH): {
			Intent intent = pm.getLaunchIntentForPackage((appList.get(index))
					.getPack());
			startActivity(intent);
			return true;
		}
		}
		return false;
	}

	private void createTable() {
		mInfoFields.addElement(createTableRow(R.string.disp_cell, -1, 0));
		mStatsFields.addElement(createTableRow(-1, R.string.disp_in, 0));
		createTableRow(0, 0, 0);
		mInfoFields.addElement(createTableRow(R.string.disp_wifi, -1, 0));
		mStatsFields.addElement(createTableRow(-1, R.string.disp_in, 0));
		createTableRow(0, 0, 0);
		mInfoFields.addElement(createTableRow(R.string.disp_usb, -1, 0));
		mStatsFields.addElement(createTableRow(-1, R.string.disp_in, 0));
		createTableRow(0, 0, 0);
	}

	private TextView createTableRow(int c1, int c2, int c3) {
		int[] cell_text_ids = { c1, c2, c3 };
		for (int i = 0; i < 3; ++i) {
			TextView txt = new TextView(this);
			if (cell_text_ids[i] == -1) {
				txt.setVisibility(View.INVISIBLE);
			} else if (cell_text_ids[i] == 0) {
				txt.setText("");
				txt.setGravity(Gravity.RIGHT);
				return txt;
			} else {
				txt.setText(getString(cell_text_ids[i]));
			}
		}
		return null;
	}

	/**
	 * Set up the notification in the status bar, which can be used to restart
	 * the NetMeter main display activity.
	 */
	private void postNotification() {
		if (notification != null)
			mNM.cancel(R.string.iconized);

		// In this sample, we'll use the same text for the ticker and the
		// expanded notification
		if (text == null)
			text = getString(R.string.app_name);

		// Set the icon, scrolling text and timestamp
		notification = new Notification(selectIcon(), text,
				System.currentTimeMillis());
		notification.flags |= Notification.FLAG_NO_CLEAR
				| Notification.FLAG_ONGOING_EVENT;

		Intent originalActivity = null;
		try {
			originalActivity = new Intent(getApplicationContext(), GUI.class);
		} catch (NullPointerException e) {
			originalActivity = new Intent(UPMTClient.getContext(), GUI.class);
		}
		originalActivity.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP
				| Intent.FLAG_ACTIVITY_SINGLE_TOP);

		// The PendingIntent to launch our activity if the user selects this
		// notification
		if (contentIntent == null)
			contentIntent = PendingIntent.getActivity(this, 0,
					originalActivity, 0);

		if (title == null)
			title = (String) getText(R.string.iconized);

		// Set the info for the views that show in the notification panel.
		try {
			notification.setLatestEventInfo(this, title, text, contentIntent);
		} catch (NullPointerException e) {
			notification.setLatestEventInfo(UPMTClient.getContext(), title,
					text, contentIntent);
		}

		// Send the notification.
		// We use a string id because it is a unique number. We use it later to
		// cancel.
		mNM.notify(R.string.iconized, notification);

	}

	private int selectIcon() {
		try {
			boolean wifi = false, wific = false, g = false, gc = false;
			for (String anchorNode : UPMTClient.getCfgANList()) {
				for (String interf : ifList) {
					if (SipSignalManager.canUseTunnel(anchorNode.split(":")[0].trim(), interf)) {
						if (interf.equals("eth0")) {
							wifi = true;
							wific = true;
						} else if (interf.equals("rmnet0")) {
							g = true;
							gc = true;
						}
					} else {
						if (interf.equals("eth0")) {
							wifi = true;
						} else if (interf.equals("rmnet0")) {
							g = true;
						}
					}
				}
			}
			String check = "";
			check += g ? "1" : "0";
			check += wifi ? "1" : "0";
			check += gc ? "1" : "0";
			check += wific ? "1" : "0";

			switch (Integer.parseInt(check)) {
			case (0000):
				return R.drawable.ic_stat_notification_3nwn;
			case (1010):
				return R.drawable.ic_stat_notification_3vwn;
			case (1000):
				return R.drawable.ic_stat_notification_3rwn;
			case (0101):
				return R.drawable.ic_stat_notification_3nwv;
			case (0100):
				return R.drawable.ic_stat_notification_3nwr;
			case (1111):
				return R.drawable.ic_stat_notification_3vwv;
			case (1110):
				return R.drawable.ic_stat_notification_3vwr;
			case (1011):
				return R.drawable.ic_stat_notification_3rwv;
			case (1100):
				return R.drawable.ic_stat_notification_3rwr;
			}
		} catch (Exception e) {
			return R.drawable.ic_stat_notification_3nwn;
		}
		return 0;
	}

	public static String getDefaultPolicy() {
		return DefaultPolicy;
	}

	// ****************************** Logs *****************************
	private void printLog(String text, int loglevel) {
		UPMTClient.printGenericLog(this, text, loglevel);
	}
}
// #endif