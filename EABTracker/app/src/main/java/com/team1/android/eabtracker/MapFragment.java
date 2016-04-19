package com.team1.android.eabtracker;


import android.app.ProgressDialog;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

// ArcGIS Runtime SDK modules
import com.esri.android.map.Layer;
import com.esri.android.map.MapView;
import com.esri.android.map.ags.ArcGISDynamicMapServiceLayer;
import com.esri.android.map.ags.ArcGISFeatureLayer;
import com.esri.android.map.ags.ArcGISLayerInfo;
import com.esri.android.map.event.OnLongPressListener;
import com.esri.android.map.event.OnSingleTapListener;
import com.esri.android.map.popup.Popup;
import com.esri.android.map.popup.PopupContainer;
import com.esri.core.geometry.Envelope;
import com.esri.core.geometry.Geometry;
import com.esri.core.geometry.Point;
import com.esri.core.geometry.SpatialReference;
import com.esri.core.map.CallbackListener;
import com.esri.core.map.Feature;
import com.esri.core.map.FeatureEditResult;
import com.esri.core.map.FeatureSet;
import com.esri.core.map.FeatureTemplate;
import com.esri.core.map.FeatureType;
import com.esri.core.map.Graphic;
import com.esri.core.map.popup.PopupInfo;
import com.esri.core.tasks.ags.query.Query;
import com.esri.core.tasks.ags.query.QueryTask;
import com.esri.android.map.event.OnStatusChangedListener;
import com.esri.core.geometry.Polygon;
import com.esri.core.geometry.GeometryEngine;

import java.util.concurrent.atomic.AtomicInteger;


public class MapFragment extends Fragment {
    private MapView map = null;
    private Polygon mCurrentMapExtent = null;
    private PopupContainer popupContainer;
    //private PopupDialog popupDialog;
    private ProgressDialog progressDialog;
    private AtomicInteger count;
    private LinearLayout editorBar;

    private final String KEY_MAP_STATE = "MapState";
    private String mMapState = null;

    public MapFragment() {
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Calling setRetainInstance() causes the Fragment instance to be retained when its Activity is destroyed and
        // recreated. This allows map Layer objects to be retained so data will not need to be fetched from the network
        // again.
        setRetainInstance(true);

    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        View rootView = inflater.inflate(R.layout.fragment_map, container, false);

        // Reinstate saved instance state (if any)
        if (savedInstanceState != null) {
            mMapState = savedInstanceState.getString(KEY_MAP_STATE, null);
        }


        // load mapview
        if (map == null) {
            // Retrieve the map and initial extent from XML layout
            map = (MapView) rootView.findViewById(R.id.map);
        }

        // Set the Esri logo to be visible, and enable map to wrap around date line.
        map.setEsriLogoVisible(true);
        map.enableWrapAround(true);

        // Restore map state (center and resolution) if a previously saved state is available, otherwise set initial extent

        if (mMapState == null) {
            SpatialReference mSR = SpatialReference.create(3857);
            Point p1 = GeometryEngine.project(-96.6, 45.98, mSR);
            Point p2 = GeometryEngine.project(-91.39, 43.89, mSR);
            Envelope mInitExtent = new Envelope(p1.getX(), p1.getY(), p2.getX(), p2.getY());
            map.setExtent(mInitExtent);
        } else {
            map.restoreState(mMapState);
        }

        // Set a listener for map status changes; this will be called when switching basemaps.
        map.setOnStatusChangedListener(new OnStatusChangedListener() {

            private static final long serialVersionUID = 1L;

            public void onStatusChanged(Object source, STATUS status) {
                // Set the map extent once the map has been initialized, and the basemap is added
                // or changed; this will be indicated by the layer initialization of the basemap layer. As there is only
                // a single layer, there is no need to check the source object.
                if (STATUS.LAYER_LOADED == status) {
                    map.setExtent(mCurrentMapExtent);
                }
            }
        });

    return rootView;

    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        // Save the map state (map center and resolution).
        if (mMapState != null) {
            outState.putString(KEY_MAP_STATE, mMapState);
        }
    }

    @Override
    public void onPause() {
        super.onPause();

        // Save map state and pause the MapView to save battery
        mMapState = map.retainState();
        map.pause();
    }

    @Override
    public void onResume() {
        super.onResume();

        // Start the MapView threads running again
        map.unpause();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();

        // Release MapView resources
        map.recycle();
        map = null;
    }

}
