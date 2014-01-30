package com.bbbz.nexus5lwp;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;

import com.bbbz.nexus5lwp.Nexus5LWP.SnowEngine;

class SensorEventHandler implements SensorEventListener {
    public SensorManager sensorMgr;

    public Sensor accelerometer;
    public Sensor compass;

    float[] gravity = new float[3];
    float[] geomag = new float[3];

    float[] inR = new float[16];
    float[] outR = new float[16];
    float[] I = new float[16];
    float[] orientVals = new float[3];
    
    public long lastUpdate;
    public long lastShakeTS;

    public float last_x, last_y, last_z;

    // FIXME: decouple this class from the engine
    private SnowEngine mEngine;

    /*
     * time smoothing constant for low-pass filter
     * 0 ² alpha ² 1 ; a smaller value basically means more smoothing
     * See: http://en.wikipedia.org/wiki/Low-pass_filter#Discrete-time_realization
     */
    static final float ALPHA = 0.15f;

    /**
     * @see http://en.wikipedia.org/wiki/Low-pass_filter#Algorithmic_implementation
     * @see http://developer.android.com/reference/android/hardware/SensorEvent.html#values
     */
    protected float[] lowPass(float[] input, float[] output) {
        if (output == null)
            return input.clone();

        for (int i=0; i<input.length; i++) {
            output[i] = output[i] + ALPHA * (input[i] - output[i]);
        }

        return output;
    }

    public SensorEventHandler(SnowEngine engine, SensorManager sm) {
        sensorMgr = sm;
        accelerometer = sensorMgr.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        compass = sensorMgr.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);

        mEngine = engine;
    }

    public void register() {  
        sensorMgr.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME);
        sensorMgr.registerListener(this, compass, SensorManager.SENSOR_DELAY_GAME);
    }

    public void unregister() {
        sensorMgr.unregisterListener(this);
    }

    @Override
    public void onAccuracyChanged(Sensor s, int arg1)
    {
    }

    @Override
    public void onSensorChanged(SensorEvent event) {

        switch (event.sensor.getType()) {  
            case Sensor.TYPE_ACCELEROMETER:
                gravity = lowPass(event.values, gravity);
                break;
            case Sensor.TYPE_MAGNETIC_FIELD:
                geomag = lowPass(event.values, geomag);
                break;
        }

        double azimuth = 0;
        double pitch = 0;
        double roll = 0;

        // checks that the rotation matrix is found
        boolean success = SensorManager.getRotationMatrix(inR, I,
                                                          gravity,
                                                          geomag);

        if (success) {
            if (mEngine.mWidth < mEngine.mHeight) {
                success = SensorManager.remapCoordinateSystem(inR, SensorManager.AXIS_Y, SensorManager.AXIS_MINUS_X, outR);
            } else {
                outR = inR;
            }

            if (success) {
                SensorManager.getOrientation(outR, orientVals);

                azimuth = Math.toDegrees(orientVals[0]);
                pitch = Math.toDegrees(orientVals[1]);
                roll = Math.toDegrees(orientVals[2]);

                mEngine.mPitch = pitch;
                mEngine.mAzimuth = azimuth;
                mEngine.mRoll = roll;
            }
        }

        // Not interested in shake at all for the Nexus 5 LWP
        return;
    }
}