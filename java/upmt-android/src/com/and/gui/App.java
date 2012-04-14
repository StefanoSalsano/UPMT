package com.and.gui;

import java.io.Serializable;

import android.graphics.drawable.Drawable;

public class App implements Serializable
{

	private static final long serialVersionUID = -236692232832894739L;
	
	private String  name;
	private String  pack;
    private String  currPolicy;
    private String  storPolicy;
    private String	currentInterf;
    private Boolean isRunning; 
    private Drawable icon;

    public App(String name, String pack, String currPolicy, String storPolicy, String ci, Boolean isRunning, Drawable icon)
    {
        super();
        this.name = name;
        this.pack = pack;
        this.currPolicy = currPolicy;
        this.storPolicy = storPolicy;
        this.currentInterf = ci;
        this.icon = icon;
        this.isRunning = isRunning;
    }

    public String getName()
    {
    	return name;
    }

    public String getPack()
    {
    	return pack;
    }

    public String getCurrPolicy()
    {
    	return currPolicy;
    }
    
    public String getStorPolicy()
    {
    	return storPolicy;
    }
    
    public String getCurrentInterf()
    {
    	return currentInterf;
    }
    
    public Drawable getIcon()
    {
    	return icon;
    }
    
    public Boolean isRunning()
    {
    	return isRunning;
    }
    
    public String toString(){
    	return  name + "-" + pack+ "-" + currPolicy+ "-" + storPolicy+ "-" + currentInterf + "-" + icon+ "-" + isRunning;
    }
}