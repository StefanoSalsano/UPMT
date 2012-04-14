package com.and.gui.utils;

import com.and.gui.R;


import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

public class AppViewCache {

        private View            baseView;
        private TextView        textViewName;
        private ImageView      	ImageViewIcon;
        private ImageView       imageViewInt1;
        private ImageView       imageViewInt2;
        private ImageView       imageViewInt3;
        private ImageView       imageViewDef;
        private ImageView       imageRunning;

        public AppViewCache(View baseView)
        {
        	this.baseView = baseView;
        }

        public TextView getTextViewName()
        {
                if(textViewName == null)
                {
                        textViewName = (TextView)baseView.findViewById(R.id.appName);
                }
                return textViewName;
        }

        public ImageView getImageViewIcon()
        {
            if(ImageViewIcon == null)
            {
                    ImageViewIcon = (ImageView)baseView.findViewById(R.id.appIcon);
            }
            return ImageViewIcon;
        }

        public ImageView getImageViewInt1()
        {
        	if (imageViewInt1 == null)
        	{
        		imageViewInt1 = (ImageView)baseView.findViewById(R.id.int1);
        	}
        	return imageViewInt1;
        }
        
        public ImageView getImageViewInt2()
        {
        	if (imageViewInt2 == null)
        	{
        		imageViewInt2 = (ImageView)baseView.findViewById(R.id.int2);
        	}
        	return imageViewInt2;
        }
        
        public ImageView getImageViewInt3()
        {
        	if (imageViewInt3 == null)
        	{
        		imageViewInt3 = (ImageView)baseView.findViewById(R.id.int3);
        	}
        	return imageViewInt3;
        }
 
        public ImageView getImageViewDef()
        {
        	if (imageViewDef == null)
        	{
        		imageViewDef = (ImageView)baseView.findViewById(R.id.custom);
        	}
        	return imageViewDef;
        }

        public ImageView getImageRunning()
        {
            if (imageRunning == null)
            {
                    imageRunning = (ImageView)baseView.findViewById(R.id.running);
            }
            return imageRunning;
        }
}