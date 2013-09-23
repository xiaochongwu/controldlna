package com.github.nutomic.controldlna;

import org.teleal.cling.model.action.ActionInvocation;
import org.teleal.cling.model.message.UpnpResponse;
import org.teleal.cling.model.meta.Device;
import org.teleal.cling.model.meta.Service;
import org.teleal.cling.model.meta.StateVariableAllowedValueRange;
import org.teleal.cling.support.avtransport.callback.Seek;
import org.teleal.cling.support.model.SeekMode;
import org.teleal.cling.support.renderingcontrol.callback.GetVolume;
import org.teleal.cling.support.renderingcontrol.callback.SetVolume;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.util.Log;

import com.github.nutomic.controldlna.service.PlayService;
import com.github.nutomic.controldlna.service.PlayServiceBinder;

/**
 * Handles connection to PlayService and provides methods related to playback.
 * 
 * @author Felix Ableitner
 *
 */
public class UpnpPlayer extends UpnpController {
	
	private static final String TAG = "UpnpPlayer";

	private PlayServiceBinder mPlayService;
	
	private long mMinVolume;
	
	private long mMaxVolume;
	
	private long mVolumeStep;
	
	private ServiceConnection mPlayServiceConnection = new ServiceConnection() {

		public void onServiceConnected(ComponentName className, IBinder service) {
			mPlayService = (PlayServiceBinder) service;
        }

        public void onServiceDisconnected(ComponentName className) {
            mPlayService = null;
        }
    };

    @Override
    public void open(Context context) {
    	super.open(context);
        context.bindService(
            new Intent(context, PlayService.class),
            mPlayServiceConnection,
            Context.BIND_AUTO_CREATE
        );    	
    }
    
    @Override
    public void close(Context context) {
    	super.close(context);
        context.unbindService(mPlayServiceConnection);	
    }

    /**
     * Returns a device service by name for direct queries.
     */
	public Service<?, ?> getService(String name) {
		return getService(mPlayService.getService().getRenderer(), name);
	}
    
    /**
     * Sets an absolute volume.
     */
    public void setVolume(long newVolume) {
    	if (mPlayService.getService().getRenderer() == null)
    		return;

    	if (newVolume > mMaxVolume) newVolume = mMaxVolume;
    	if (newVolume < mMinVolume) newVolume = mMinVolume;
    	
		mUpnpService.getControlPoint().execute(
				new SetVolume(getService("RenderingControl"), newVolume) {
			
			@SuppressWarnings("rawtypes")
			@Override
			public void failure(ActionInvocation invocation, 
					UpnpResponse operation, String defaultMessage) {
				Log.d(TAG, "Failed to set new Volume: " + defaultMessage);
			}
		});
    }


	/**
	 * Increases or decreases volume relative to current one.
	 * 
	 * @param amount Amount to change volume by (negative to lower volume).
	 */
    private void changeVolume(final long amount) {
    	if (mPlayService.getService().getRenderer() == null)
    		return;
		
		mUpnpService.getControlPoint().execute(
    			new GetVolume(getService("RenderingControl")) {
			
			@SuppressWarnings("rawtypes")
			@Override
			public void failure(ActionInvocation invocation, 
					UpnpResponse operation, String defaultMessage) {
				Log.w(TAG, "Failed to get current Volume: " + defaultMessage);
			}
			
			@SuppressWarnings("rawtypes")
			@Override
			public void received(ActionInvocation invocation, int currentVolume) {
				setVolume(currentVolume + amount);
			}
		});	
    }
    
    /**
     * Increases the device volume by a minimum volume step.
     */
    public void increaseVolume() {
    	changeVolume(mVolumeStep);
    }
    
    /**
     * Decreases the device volume by a minimum volume step.
     */
    public void decreaseVolume() {
    	changeVolume(- mVolumeStep);
    }
    
    /**
     * Selects the renderer for playback, applying its minimum and maximum volume.
     */
    public void selectRenderer(Device<?, ?, ?> renderer) {
    	mPlayService.getService().setRenderer(renderer);
    	
        if (getService("RenderingControl").getStateVariable("Volume") != null) {
        	StateVariableAllowedValueRange volumeRange = 
        			getService("RenderingControl").getStateVariable("Volume")
        					.getTypeDetails().getAllowedValueRange();
        	mMinVolume = volumeRange.getMinimum();
        	mMaxVolume = volumeRange.getMaximum();
        	mVolumeStep = volumeRange.getStep();
        }
        else {
        	mMinVolume = 0;
        	mMaxVolume = 100;
        	mVolumeStep = 1;    	
        }
        // Hack, needed as using a smaller step seems to not 
        // increase volume on some devices.
        mVolumeStep = 4;
    }
    
    /**
     * Seeks to the given absolute time in seconds.
     */
    public void seek(int absoluteTime) {
    	if (mPlayService.getService().getRenderer() == null)
    		return;
    	
		mUpnpService.getControlPoint().execute(new Seek(
    			getService(mPlayService.getService().getRenderer(), "AVTransport"), 
    			SeekMode.REL_TIME, 
    			Integer.toString(absoluteTime)) {
			
			@SuppressWarnings("rawtypes")
			@Override
			public void failure(ActionInvocation invocation, 
					UpnpResponse operation, String defaultMessage) {
				Log.w(TAG, "Seek failed: " + defaultMessage);
			}
		});		
    	
    }
    
    /**
     * Returns the service that handles actual playback.
     */
    public PlayService getPlayService() {
    	return mPlayService.getService();
    }

}
