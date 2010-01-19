/*
 Copyright 2009 AdMob, Inc.
 
    Licensed under the Apache License, Version 2.0 (the "License");
 you may not use this file except in compliance with the License.
 You may obtain a copy of the License at
 
  http://www.apache.org/licenses/LICENSE-2.0
 
 Unless required by applicable law or agreed to in writing, software
 distributed under the License is distributed on an "AS IS" BASIS,
 WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 See the License for the specific language governing permissions and
 limitations under the License.
*/

package com.adwhirl;

import java.io.IOException;

import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;

import com.adwhirl.adapters.AdWhirlAdapter;
import com.adwhirl.obj.Custom;
import com.adwhirl.obj.Extra;
import com.adwhirl.obj.Ration;
import com.adwhirl.util.AdWhirlUtil;

import com.qwapi.adclient.android.view.QWAdView;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.GradientDrawable.Orientation;
import android.net.Uri;
import android.os.Handler;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.ImageView.ScaleType;

public class AdWhirlLayout extends FrameLayout {
	public final Context context;
	
	// Only the UI thread can update the UI, so we need these for callbacks
	public Handler handler;
	public Runnable adRunnable;
	public Runnable customRunnable;
	public Runnable genericRunnable;
	public Runnable viewRunnable;
	
	public Extra extra;
	
	// The current custom ad
	public Custom custom;
	
	// This is just so our threads can reference us explicitly
	public FrameLayout superView;
	
	public Ration activeRation;
	public Ration nextRation;
	
	public ViewGroup nextView;
	
	// The Quattro callbacks don't contain a reference to the view, so we keep it here
	public QWAdView quattroView;
	
	public AdWhirlInterface adWhirlInterface;
	
	public AdWhirlManager adWhirlManager;
	
	public AdWhirlLayout(final Context context, String keyAdWhirl) {
		super(context);
		this.context = context;
		this.superView = this;
		
		AdWhirlUtil.keyAdWhirl = keyAdWhirl;
		
		handler = new Handler();
		// Callback for external networks
		adRunnable = new Runnable() {
			public void run() {
				handleAd();
			}
		};
		// Callback for custom ads
		customRunnable = new Runnable() {
			public void run() {
				handleCustom();
			}
		};
		// Callback for generic notifications
		genericRunnable = new Runnable() {
			public void run() {
				handleGeneric();
			}
		};
		// Callback for pushing views from ad callbacks
		viewRunnable = new Runnable() {
			public void run() {
				if(nextView == null) {
					return;
				}
				
				pushSubView(nextView);
			}
		};
		
		Thread thread = new Thread() {
			public void run() {
				adWhirlManager = new AdWhirlManager(context);
				extra = adWhirlManager.getExtra();
				if(extra == null) {
					Log.e(AdWhirlUtil.ADWHIRL, "Unable to get configuration info or bad info, exiting AdWhirl");
					return;
				}
				
				rotateAd();
			}
		};
		thread.start();
	}
	
	private void rotateAd() {
		Log.i(AdWhirlUtil.ADWHIRL, "Rotating Ad");
		nextRation = adWhirlManager.getRation();
		post();
	}
	
	// Helper function to pick the appropriate callback
	private void post() {
		if(nextRation == null) {
			Log.e(AdWhirlUtil.ADWHIRL, "nextRation is null!");
			rotateAd();
		}
		else if(nextRation.type == 9) {
			custom = adWhirlManager.getCustom(nextRation.nid);
			handler.post(customRunnable);
		}
		else if(nextRation.type == 16) {
			handler.post(genericRunnable);
		}
		else {
			handler.post(adRunnable);
		}
	}
	
	// Initialize the proper ad view from nextRation
	private void handleAd() {
		String rationInfo = String.format("Showing ad:\n\tnid: %s\n\tname: %s\n\ttype: %d\n\tkey: %s\n\tkey2: %s", nextRation.nid, nextRation.name, nextRation.type, nextRation.key, nextRation.key2);
		Log.d(AdWhirlUtil.ADWHIRL, rationInfo);

		AdWhirlAdapter adapter = AdWhirlAdapter.getAdapter(this, nextRation);
		if(adapter != null) {
			adapter.handle();
		}
	}
	
	// Rotate immediately
	public void rotateThreadedNow() {
		Thread thread = new Thread() {
			public void run() {
				rotateAd();
			}
		};
		thread.start();
	}
	
	// Rotate in extra.cycleTime seconds
	public void rotateThreadedDelayed() {
		Thread thread = new Thread() {
			public void run() {
				try {
					Log.d(AdWhirlUtil.ADWHIRL, "Will call rotateAd() in " + extra.cycleTime + " seconds");
					Thread.sleep(extra.cycleTime * 1000);
				} catch (InterruptedException e) {
					Log.e(AdWhirlUtil.ADWHIRL, "Caught InterruptedException in rotateThreadedDelayed()");
					e.printStackTrace();
				}
				rotateAd();
			}
		};
		thread.start();
	}
	
	private void handleCustom() {
		if(this.custom == null) {
			rotateThreadedNow();
			return;
		}
		
		switch(this.custom.type) {
			case AdWhirlUtil.CUSTOM_TYPE_BANNER:
				Log.d(AdWhirlUtil.ADWHIRL, "Serving custom type: banner");
				RelativeLayout bannerView = new RelativeLayout(this.context);
				if(custom.image == null) {
					rotateThreadedNow();
					return;
				}
				ImageView bannerImageView = new ImageView(this.context);
				bannerImageView.setImageDrawable(custom.image);
				bannerImageView.setScaleType(ScaleType.CENTER);
				RelativeLayout.LayoutParams bannerViewParams = new RelativeLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
				bannerView.addView(bannerImageView, bannerViewParams);
				pushSubView(bannerView);
				break;
				
			case AdWhirlUtil.CUSTOM_TYPE_ICON:
				Log.d(AdWhirlUtil.ADWHIRL, "Serving custom type: icon");
				RelativeLayout iconView = new RelativeLayout(this.context);
				if(custom.image == null) {
					rotateThreadedNow();
					return;
				}
 				ImageView blendView = new ImageView(this.context);
 				int backgroundColor = Color.rgb(extra.bgRed, extra.bgGreen, extra.bgBlue);
				GradientDrawable blend = new GradientDrawable(Orientation.TOP_BOTTOM, new int[] {Color.WHITE, backgroundColor, backgroundColor, backgroundColor}); 
				blendView.setBackgroundDrawable(blend);
				RelativeLayout.LayoutParams blendViewParams = new RelativeLayout.LayoutParams(LayoutParams.FILL_PARENT, LayoutParams.FILL_PARENT);
				iconView.addView(blendView, blendViewParams);
				ImageView iconImageView = new ImageView(this.context);
				iconImageView.setImageDrawable(custom.image);
				iconImageView.setId(10);
				iconImageView.setPadding(4, 0, 6, 0);
				iconImageView.setScaleType(ScaleType.CENTER);
				RelativeLayout.LayoutParams iconViewParams = new RelativeLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.FILL_PARENT);
				iconView.addView(iconImageView, iconViewParams);
				ImageView frameImageView = new ImageView(this.context);
				frameImageView.setImageResource(R.drawable.ad_frame);
				frameImageView.setPadding(4, 0, 6, 0);
				frameImageView.setScaleType(ScaleType.CENTER);
				RelativeLayout.LayoutParams frameViewParams = new RelativeLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.FILL_PARENT);
				iconView.addView(frameImageView, frameViewParams);
				TextView iconTextView = new TextView(this.context);
				iconTextView.setText(custom.description);
				iconTextView.setTypeface(Typeface.DEFAULT_BOLD, 1);
				iconTextView.setTextColor(Color.rgb(extra.fgRed, extra.fgGreen, extra.fgBlue));
				RelativeLayout.LayoutParams textViewParams = new RelativeLayout.LayoutParams(LayoutParams.FILL_PARENT, LayoutParams.FILL_PARENT);
				textViewParams.addRule(RelativeLayout.RIGHT_OF, iconImageView.getId());
				textViewParams.addRule(RelativeLayout.ALIGN_PARENT_TOP);
				textViewParams.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
				textViewParams.addRule(RelativeLayout.CENTER_VERTICAL);
				textViewParams.addRule(RelativeLayout.CENTER_IN_PARENT);
				iconTextView.setGravity(Gravity.CENTER_VERTICAL);
				iconView.addView(iconTextView, textViewParams);
				pushSubView(iconView);
				break;
				
			default:
				Log.w(AdWhirlUtil.ADWHIRL, "Unknown custom type!");
				rotateThreadedNow();
				return;
		}
		
		rotateThreadedDelayed();
	}
	
	// Remove old views and push the new one
	private void pushSubView(ViewGroup subView) {
		this.superView.removeAllViews();
		
		LayoutParams adWhirlParams = new LayoutParams(LayoutParams.FILL_PARENT, LayoutParams.FILL_PARENT);
		superView.addView(subView, adWhirlParams);
		Log.d(AdWhirlUtil.ADWHIRL, "Added subview");
		superView.invalidate();
		
		this.activeRation = nextRation;
		countImpressionThreaded();
	}
	
	public void rollover() {
		nextRation = adWhirlManager.getRollover();
		post();
	}
	
	public void rolloverThreaded() {
		Thread thread = new Thread() {
			public void run() {
				nextRation = adWhirlManager.getRollover();
				post();
			}
		};
		thread.start();
	}

	private void countImpressionThreaded() {
		Log.d(AdWhirlUtil.ADWHIRL, "Sending metrics request for impression");
		Thread thread = new Thread() {
			public void run() {
		        HttpClient httpClient = new DefaultHttpClient();
		        
		        String url = String.format(AdWhirlUtil.urlImpression, AdWhirlUtil.keyAdWhirl, activeRation.nid, AdWhirlUtil.VERSION);
		        HttpGet httpGet = new HttpGet(url); 
		 
		        try {
		            httpClient.execute(httpGet);
		        } catch (ClientProtocolException e) {
		        	Log.e(AdWhirlUtil.ADWHIRL, "Caught ClientProtocolException in countImpressionThreaded()");
		        	e.printStackTrace();
		        } catch (IOException e) {
		        	Log.e(AdWhirlUtil.ADWHIRL, "Caught IOException in countImpressionThreaded()");
		        	e.printStackTrace();
		        }
			}
		};
		thread.start();
	}
	
	private void countClickThreaded() {
		Log.d(AdWhirlUtil.ADWHIRL, "Sending metrics request for click");
		Thread thread = new Thread() {
			public void run() {
		        HttpClient httpClient = new DefaultHttpClient();
		        
		        String url = String.format(AdWhirlUtil.urlClick, AdWhirlUtil.keyAdWhirl, activeRation.nid, AdWhirlUtil.VERSION);
		        HttpGet httpGet = new HttpGet(url); 
		 
		        try {
		            httpClient.execute(httpGet);
		        } catch (ClientProtocolException e) {
		        	Log.e(AdWhirlUtil.ADWHIRL, "Caught ClientProtocolException in countClickThreaded()");
		        	e.printStackTrace();
		        } catch (IOException e) {
		        	Log.e(AdWhirlUtil.ADWHIRL, "Caught IOException in countClickThreaded()");
		        	e.printStackTrace();
		        }
			}
		};
		thread.start();
	}
	
	//We intercept clicks to provide raw metrics
	public boolean onInterceptTouchEvent(MotionEvent event) {  
		switch(event.getAction()) {
		//Sending on an ACTION_DOWN isn't 100% correct... user could have touched down and dragged out. Unlikely though.
		case MotionEvent.ACTION_DOWN:
			Log.d(AdWhirlUtil.ADWHIRL, "Intercepted ACTION_DOWN event");
			countClickThreaded();
			
			if(activeRation.type == 9) {
				Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(custom.link));
				this.context.startActivity(intent);
			}
			break;
		}
	
		// Return false so subViews can process event normally.
		return false;
	}
	
	public interface AdWhirlInterface {
		public void adWhirlGeneric();
	}
	
	public void setAdWhirlInterface(AdWhirlInterface i) {
		this.adWhirlInterface = i;
	}
	
	private void handleGeneric() {
		Log.d(AdWhirlUtil.ADWHIRL, "Generic notification request initiated");
		
		//If the user set a handler for generic notifications, call it
		if(this.adWhirlInterface != null) {
			this.adWhirlInterface.adWhirlGeneric();
		}
		
		adWhirlManager.resetRollover();
		rotateThreadedDelayed();
	}
}