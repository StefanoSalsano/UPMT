package com.and.gui.activity;

import java.util.ArrayList;
import java.util.Arrays;

import upmt.client.UPMTClient;


import com.and.gui.R;


import android.app.Activity;
import android.os.Bundle;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.CheckedTextView;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.Toast;

public class PolicyEditor extends Activity
{

	private String apTitle;
	private String apPack;
	private ListView interfacesLV;
	private ArrayAdapter<String> aa;
	private ArrayList<String> policy;
	private ArrayList<String> iflist;
	private ImageButton ibAdd;
	private ImageButton ibDel;
	private ImageButton ibUp;
	private ImageButton ibDown;
	CheckedTextView textView;
	private int index = -1; //index of selected item in listview
	private boolean clicked = false; //used when move an item from button to allow following moves

	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.policy_editor);

		String activityTitle = getString(R.string.policyEditor_label);
		Bundle extras = getIntent().getExtras();
		if (extras != null)
		{
			activityTitle += " - ";
			apTitle = extras.getString(getString(R.string.APP_NAME_TAG));
			apPack = extras.getString(getString(R.string.PACK_NAME_TAG));
			activityTitle += apTitle;
		}

		this.setTitle(activityTitle);

		interfacesLV = (ListView)findViewById(R.id.lvInt);
		policy = new ArrayList<String>(Arrays.asList(extras.getStringArray(getString(R.string.CURRENT_POLICY_TAG))));

		if(policy.get(0).equals("PriorityList") || policy.get(0).equals("Block") )
			policy.remove(0);

		aa = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_checked, policy);
		interfacesLV.setAdapter(aa);

		iflist = extras.getStringArrayList("iflist");
		iflist.add(getString(R.string.any));

		registerForContextMenu(interfacesLV);
		ibAdd = (ImageButton) findViewById(R.id.ibAdd);
		(ibUp = (ImageButton) findViewById(R.id.ibUp)).setEnabled(false);
		(ibDown = (ImageButton) findViewById(R.id.ibDown)).setEnabled(false);
		(ibDel = (ImageButton) findViewById(R.id.ibDel)).setEnabled(false);
		registerForContextMenu(ibAdd);


		interfacesLV.setOnItemClickListener(new OnItemClickListener()
		{
			public void onItemClick(AdapterView<?> parent, View view, int position, long id)
			{
				clicked = true;
				index = index!=position?position:-1;
				textView = (CheckedTextView)view;
				interfacesLV.setItemChecked(position, !textView.isChecked());
				buttonCheck();
			}
		});
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu)
	{
		super.onCreateOptionsMenu(menu);


		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.policy_menu, menu);
		SubMenu subMenu = menu.addSubMenu(0, 444, Menu.NONE, R.string.add);
		for(int i=0; i<iflist.size(); i++) 
			subMenu.add(0, i, Menu.NONE, iflist.get(i));
		
		return true;
	}

	@Override
	public boolean onPrepareOptionsMenu(Menu menu)
	{
		super.onPrepareOptionsMenu(menu);
		int idx = interfacesLV.getCheckedItemPosition();
		MenuItem delItem = menu.findItem(R.id.del);
		delItem.setEnabled(idx > -1);
		MenuItem upItem = menu.findItem(R.id.up);
		upItem.setEnabled(idx > 0);
		MenuItem downItem = menu.findItem(R.id.down);
		downItem.setEnabled(idx != -1 && idx != interfacesLV.getChildCount()-1);
		return true;
	}

	@Override
	public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo)
	{
		super.onCreateContextMenu(menu, v, menuInfo);
			
		
		switch(v.getId())
		{
			case(R.id.lvInt):
			{
				AdapterContextMenuInfo info = (AdapterContextMenuInfo) menuInfo;
				index = info.position;
				menu.setHeaderTitle("Selected interface");
	
				MenuInflater inflater = getMenuInflater();
				inflater.inflate(R.menu.policy_menu, menu);
				if(index == 0)
					menu.removeItem(R.id.up);
				if(index == interfacesLV.getChildCount()-1)
					menu.removeItem(R.id.down);
				SubMenu subMenu = menu.addSubMenu(0, 444, Menu.NONE, R.string.add);
				for(int i=0; i<iflist.size(); i++) 
					subMenu.add(0, i, Menu.NONE, iflist.get(i));
				break;
			}
			case(R.id.ibAdd):
			{
				menu.setHeaderTitle("Selected interface");
							
				for(int i=0; i<iflist.size(); i++){
					menu.add(0, i, Menu.NONE, iflist.get(i));
				}
				break;
			}
		}
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item)
	{
		super.onOptionsItemSelected(item);
		index = interfacesLV.getCheckedItemPosition();
		//	    interfacesLV.clearChoices();
		int itemId = item.getItemId();
		switch (itemId)
		{
		case (R.id.del):
		{
			removeItem(index);
			return true;
		}
		case (R.id.up):
		{
			moveUpItem(index);
			return true;
		}
		case (R.id.down):
		{
			moveDownItem(index);
			return true;
		}
		case (444):
		{
			return true;
		}
		default:
		{
			for(int i=0; i<iflist.size(); i++)
			{
				if(itemId == i)
				{
					addNewItem(index, iflist.get(i));
					return true;
				}
			}
		}
		}
		return false;
	}

	@Override
	public boolean onContextItemSelected(MenuItem item)
	{
		super.onContextItemSelected(item);
		//	    index = interfacesLV.getSelectedItemPosition();
		//	    interfacesLV.clearChoices();
		int itemId = item.getItemId();
		switch (itemId)
		{
		case (R.id.del):
		{
			removeItem(index);
			return true;
		}
		case (R.id.up):
		{
			moveUpItem(index);
			return true;
		}
		case (R.id.down):
		{
			moveDownItem(index);
			return true;
		}
		case (444):
		{
			return true;
		}
		default:
		{
			for(int i=0; i<iflist.size(); i++)
			{
				if(itemId == i)
				{
					addNewItem(index, iflist.get(i));
					return true;
				}
			}
		}
		}
		return false;
	}

	public void myClickHandler(View view)
	{
		if(clicked) index = interfacesLV.getCheckedItemPosition();
		clicked = false;
		//	    interfacesLV.clearChoices();
		switch (view.getId())
		{
			case (R.id.ibDel):
			{
				removeItem(index);
				break;
			}
			case (R.id.ibAdd):
			{
				System.out.println("add");
				openContextMenu(view);
				break;
			}
			case (R.id.ibUp):
			{
				moveUpItem(index);
				break;
			}
			case (R.id.ibDown):
			{
				moveDownItem(index);
				break;
			}
			case (R.id.btnApply):
			{
				GUI.onApplyPolicy(apPack, policyString());
				break;
			}
			case (R.id.btnSave):
			{
				GUI.onSavePolicy(apPack, policyString());
				break;
			}
		}
	}

	private String policyString()
	{
		String ret = "PriorityList";
		for(String pol: policy) ret += " " + pol;

		if(policy.size() == 0)
			ret = "Block";

		return ret;
	}

	private void buttonCheck()
	{
		if(index <= 0)
			ibUp.setEnabled(false);
		else
			ibUp.setEnabled(true);
		if(index == policy.size() -1 || index == -1)
			ibDown.setEnabled(false);
		else
			ibDown.setEnabled(true);
		if(index == -1)
			ibDel.setEnabled(false);
		else
			ibDel.setEnabled(true);
	}

	private void addNewItem(int position, String item)
	{
		if(position == -1) 
			position = policy.size();

		if(!policy.contains(item))
			policy.add(position, item);
		else
			Toast.makeText(UPMTClient.getContext(), "already added interface",
					Toast.LENGTH_SHORT).show();


		aa.notifyDataSetChanged();
	}

	private void removeItem(int _index)
	{
		//    	if(_index != -1)
		//    	{
		policy.remove(_index);
		interfacesLV.setItemChecked(_index, false);
		index = -1;
		buttonCheck();
		aa.notifyDataSetChanged();
		//    	}
	}

	private void moveUpItem(int _index)
	{
		policy.add(_index-1, policy.remove(_index));
		index--;
		interfacesLV.setItemChecked(_index, false);
		interfacesLV.setItemChecked(index, true);
		buttonCheck();
		aa.notifyDataSetChanged();
	}

	private void moveDownItem(int _index)
	{
		policy.add(_index+1, policy.remove(_index));
		index++;
		interfacesLV.setItemChecked(_index, false);
		interfacesLV.setItemChecked(index, true);
		buttonCheck();
		aa.notifyDataSetChanged();
	}
}
