package com.example.chen.mapsensor;

import android.Manifest;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.Toast;
import android.widget.ToggleButton;

import com.baidu.mapapi.SDKInitializer;
import com.baidu.mapapi.map.BaiduMap;
import com.baidu.mapapi.map.BitmapDescriptor;
import com.baidu.mapapi.map.BitmapDescriptorFactory;
import com.baidu.mapapi.map.MapStatus;
import com.baidu.mapapi.map.MapStatusUpdate;
import com.baidu.mapapi.map.MapStatusUpdateFactory;
import com.baidu.mapapi.map.MapView;
import com.baidu.mapapi.map.MyLocationConfiguration;
import com.baidu.mapapi.map.MyLocationData;
import com.baidu.mapapi.model.LatLng;
import com.baidu.mapapi.utils.CoordinateConverter;
import com.example.chen.mapsensor.R;

/**
 * Created by Chen on 2016/12/7.
 */

public class MainActivity extends AppCompatActivity {
    private MapView mapView;
    private ToggleButton toggleButton;
    private SensorManager sensorManager;
    private Sensor magneticSensor, accelerometerSensor;
    private LocationManager locationManager;
    private SensorEventListener sensorEventListener;
    private LocationListener locationListener;
    private String provider;
    private Bitmap bitmap;
    private BitmapDescriptor bitmapDescriptor;
    private MyLocationConfiguration config;
    private float[] accelerometerValues;
    private float[] magneticFieldValues;
    private boolean canShake = true;

    private String[] permissions = new String[]{
            android.Manifest.permission.ACCESS_NETWORK_STATE,
            android.Manifest.permission.INTERNET,
            Manifest.permission.READ_SYNC_SETTINGS,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.WAKE_LOCK,
            Manifest.permission.CHANGE_WIFI_STATE,
            Manifest.permission.ACCESS_WIFI_STATE,
            Manifest.permission.GET_TASKS,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.WRITE_SETTINGS};
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SDKInitializer.initialize(getApplicationContext());
        setContentView(R.layout.main_layout);
        RequestPermission();
        findViews();
        InitialMap();
    }

    private void UpdateLocation() {
        MyLocationData.Builder data = new MyLocationData.Builder();
        LatLng mylocation = getLocation();
        if (mylocation == null) return;
        data.latitude(mylocation.latitude);
        data.longitude(mylocation.longitude);
        data.direction(getTowards());
        mapView.getMap().setMyLocationData(data.build());
    }

    private void InitialMap() {
        toggleButton.setChecked(true);
        bitmap = Bitmap.createScaledBitmap(BitmapFactory.decodeResource(getResources(),
                R.mipmap.pointer), 100, 100, true);
        bitmapDescriptor = BitmapDescriptorFactory.fromBitmap(bitmap);
        mapView.getMap().setMyLocationEnabled(true);
        config = new MyLocationConfiguration(
                MyLocationConfiguration.LocationMode.NORMAL, true, bitmapDescriptor);
        mapView.getMap().setMyLocationConfigeration(config);
        UpdateLocation();
        CheckIfNeedToCenterlize();
    }

    private void CheckIfNeedToCenterlize() {
        if (toggleButton.isChecked()) {
            MapStatus mapStatus = new MapStatus.Builder().target(getLocation()).build();
            MapStatusUpdate mapStatusUpdate = MapStatusUpdateFactory.newMapStatus(mapStatus);
            mapView.getMap().setMapStatus(mapStatusUpdate);
        }
    }

    private void findViews() {
        mapView = (MapView) findViewById(R.id.mapView);
        toggleButton = (ToggleButton)findViewById(R.id.tb_center);
        toggleButton.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    CheckIfNeedToCenterlize();
                }
            }
        });
        mapView.getMap().setOnMapTouchListener(new BaiduMap.OnMapTouchListener() {
            @Override
            public void onTouch(MotionEvent motionEvent) {
                switch (motionEvent.getAction()) {
                    case MotionEvent.ACTION_MOVE:
                        toggleButton.setChecked(false);
                }
            }
        });
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        magneticSensor = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        accelerometerSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        accelerometerValues = new float[3];
        magneticFieldValues = new float[3];
        sensorEventListener = new SensorEventListener() {
            @Override
            public void onSensorChanged(SensorEvent event) {
                switch (event.sensor.getType()) {
                    case Sensor.TYPE_ACCELEROMETER:
                        accelerometerValues = event.values;
                        CheckIfShake();
                        UpdateLocation();
                        CheckIfNeedToCenterlize();
                        break;
                    case Sensor.TYPE_MAGNETIC_FIELD:
                        magneticFieldValues = event.values;
                        break;
                }
            }

            @Override
            public void onAccuracyChanged(Sensor sensor, int accuracy) {}
        };

        if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
            provider = LocationManager.NETWORK_PROVIDER;
        } else {
            provider = LocationManager.GPS_PROVIDER;
        }

        locationListener = new LocationListener() {
            @Override
            public void onLocationChanged(Location location) {
                UpdateLocation();
                CheckIfNeedToCenterlize();
            }

            @Override
            public void onStatusChanged(String provider, int status, Bundle extras) {}

            @Override
            public void onProviderEnabled(String provider) {}

            @Override
            public void onProviderDisabled(String provider) {}
        };
        try {
            locationManager.requestLocationUpdates(provider, 0, 0, locationListener);
        } catch (SecurityException e) {
            System.out.println("更新位置被拒绝");
        }
    }

    private void CheckIfShake() {
        float x = accelerometerValues[0];
        float y = accelerometerValues[1];
        float z = accelerometerValues[2];
        if ((Math.abs(x) > 15 || Math.abs(y) > 15 || Math.abs(z) > 15) && canShake) {
            canShake = false;
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("客官打个赏可好(*^__^*) ");
            View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_layout, null);
            builder.setView(dialogView);
            builder.setPositiveButton("朕知道了", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    canShake = true;
                }
            });
            builder.setCancelable(false);
            builder.create();
            builder.show();
        }
    }

    private void RequestPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N)
            return;
        if (!CheckPermision()) {
            System.out.println("Require for permission");
            requestPermissions(permissions, 0);
        }
    }

    private boolean CheckPermision() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            return true;
        } else {
            for (int i = 0; i < permissions.length; i++) {
                if (checkSelfPermission(permissions[i]) != PackageManager.PERMISSION_GRANTED) {
                    return false;
                }
            }
            return true;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mapView.onDestroy();
    }

    @Override
    protected void onResume() {
        super.onResume();
        mapView.onResume();
        sensorManager.registerListener(
                sensorEventListener, magneticSensor, SensorManager.SENSOR_DELAY_GAME);
        sensorManager.registerListener(
                sensorEventListener, accelerometerSensor, SensorManager.SENSOR_DELAY_GAME);


    }

    @Override
    protected void onPause() {
        super.onPause();
        mapView.onPause();
        sensorManager.unregisterListener(sensorEventListener);
        try {
            locationManager.removeUpdates(locationListener);
        } catch (SecurityException e) {
            System.out.println("解除位置监听失败");
        }
    }

    private float getTowards() {
        float[] R = new float[9];
        float[] values = new float[3];

        SensorManager.getRotationMatrix(R, null, accelerometerValues, magneticFieldValues);
        SensorManager.getOrientation(R, values);

        return (float)Math.toDegrees(values[0]);
    }

    private LatLng getLocation() {
       Location location = null;
        try {
            location = locationManager.getLastKnownLocation(provider);
        } catch (SecurityException e) {
            System.out.println("获取位置服务被拒绝");
        }
        return LocationConvertToLatlng(location);
    }

    private LatLng LocationConvertToLatlng(Location location) {
        if (location == null) {
            System.out.println("还没有进行定位");
            return null;
        }
        CoordinateConverter converter  = new CoordinateConverter();
        converter.from(CoordinateConverter.CoordType.GPS);
        converter.coord(new LatLng(location.getLatitude(), location.getLongitude()));
        LatLng desLatLng = converter.convert();
        return desLatLng;
    }
}
