package com.and.gui.activity;


import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;



public class Setting extends Activity {
	
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		startup();
	}

	@Override
	protected void onStart() {
		super.onStart();
		startup();
	}

	private void startup(){
		Intent intent = getIntent();
		Bundle extras = intent.getExtras();
		
		String vipaFix = extras.getString("vipaFix");
		String defaultAN = extras.getString("defaultAN");
		int maxANs = extras.getInt("maxANs");
		String ANs = extras.getString("ANs");
		String ANsStatuses = extras.getString("ANsStatuses");
		String tunnelDetails = extras.getString("tunnelDetails");


		ScrollView main_layout = new ScrollView(this);
		setContentView(main_layout);
		LinearLayout layout = new LinearLayout(this);
		layout.setOrientation(LinearLayout.VERTICAL);
		main_layout.addView(layout);
		
		TextView tv = new TextView(getApplicationContext());
		tv.setText("Default AN: " + defaultAN);
		tv.setTextSize(20);
	    layout.addView(tv);
		
	    tv = new TextView(getApplicationContext());
		tv.setText("VipaFix: " + vipaFix);
		tv.setTextSize(20);
	    layout.addView(tv);

	    tv = new TextView(getApplicationContext());
		tv.setText("Maximum ANs: " + maxANs);
		tv.setTextSize(20);
	    layout.addView(tv);
	    
	    
	    String[] an = ANs.split(";");
	    String[] statuses = ANsStatuses.split(";");


		for(int i=0; i < an.length; i++)
		{
		    tv=new TextView(getApplicationContext());
		    
		    boolean disconnected = true;
		    for(int j = 0; j < statuses.length; j++ ){
		    	if(statuses[j].startsWith(an[i].split(":")[0]) && statuses[j].split(":")[2].startsWith("c")){
		    		disconnected = false;
		    		break;		    		
		    	}
		    		
		    }
			tv.setTextSize(20);
		    tv.setText("\nAN: " + an[i] + "\nStatus: " + (disconnected ? "DISCONNECTED!" : "CONNECTED!"));
		    layout.addView(tv);
		}
		
	    String[] tunnels = tunnelDetails.split(";");


		for(int i=0; i < tunnels.length; i++)
		{
		    tv=new TextView(getApplicationContext());
			tv.setTextSize(20);
		    tv.setText("\nTunnel " + i + "\n" + tunnels[i]);
		    layout.addView(tv);
		}

	}
	
}
