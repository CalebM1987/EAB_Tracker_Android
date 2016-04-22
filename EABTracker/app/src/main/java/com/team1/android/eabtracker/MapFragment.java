package com.team1.android.eabtracker;


import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.Toast;
import android.widget.Button;
import android.view.ViewGroup.LayoutParams;
import android.provider.MediaStore;
import android.view.View.OnClickListener;
import android.net.Uri;
import android.os.AsyncTask;

// ArcGIS Runtime SDK modules
import com.esri.android.map.Layer;
import com.esri.android.map.MapView;
import com.esri.android.map.ags.ArcGISDynamicMapServiceLayer;
import com.esri.android.map.ags.ArcGISFeatureLayer;
import com.esri.android.map.ags.ArcGISLayerInfo;
import com.esri.android.map.event.OnLongPressListener;
import com.esri.android.map.event.OnSingleTapListener;
import com.esri.android.map.popup.Popup;
import com.esri.android.map.LocationDisplayManager;
import com.esri.android.map.popup.PopupContainer;
import com.esri.core.geometry.Envelope;
import com.esri.core.geometry.Geometry;
import com.esri.core.geometry.LinearUnit;
import com.esri.core.geometry.Point;
import com.esri.core.geometry.SpatialReference;
import com.esri.core.geometry.Unit;
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

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicInteger;


public class MapFragment extends Fragment {
    private MapView map = null;
    private Polygon mCurrentMapExtent = null;
    private PopupContainer popupContainer;
    private PopupDialog popupDialog;
    private ProgressDialog progressDialog;
    private AtomicInteger count;
    private LinearLayout editorBar;
    private LocationDisplayManager locationManager;
    private SpatialReference mSR = SpatialReference.create(3857);
    private SpatialReference wgsSR = SpatialReference.create(4326);
    private Graphic newSighting;

    // feature layers
    private ArcGISFeatureLayer sightings;
    private ArcGISFeatureLayer counties;

    private final String KEY_MAP_STATE = "MapState";
    private String mMapState = null;

    private Button reportButton;
    static final int REQUEST_IMAGE_CAPTURE = 1;

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

        reportButton = (Button) rootView.findViewById(R.id.ReportButton);
        reportButton.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                addSighting();
            }
        });

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
        Log.d("test", "map is loaded outside listener: " + map.isLoaded());
        Layer[] layers = map.getLayers();
        for (Layer layer: layers){
            System.out.print("layer " + layer.getName());
            Log.d("layer", "layer: " + layer.getName());
        }


        // Restore map state (center and resolution) if a previously saved state is available, otherwise set initial extent

        if (mMapState == null) {
            //SpatialReference mSR = SpatialReference.create(3857);
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

                    if (source instanceof ArcGISFeatureLayer){
                        ArcGISFeatureLayer agsLayer = (ArcGISFeatureLayer) source;
                        Log.d("layer", "loaded layer " + agsLayer.getName());
                        if (agsLayer.getName().equals("EAB Siting")){
                            sightings =  agsLayer;

                        }
                        else if (agsLayer.getName().equals("County Boundary")){
                            counties = agsLayer;
                        }
                    }
                }

                else if (STATUS.INITIALIZED == status){

                    // add location
                    locationManager = map.getLocationDisplayManager();
                    locationManager.setAutoPanMode(LocationDisplayManager.AutoPanMode.NAVIGATION);
                    locationManager.setWanderExtentFactor(0.75f);
                    locationManager.start();
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

    private void addSighting() {
        if (map.isLoaded()){
            Point userLocation = locationManager.getPoint();

            FeatureType[] featuretypes = sightings.getTypes();
            if (featuretypes == null || featuretypes.length < 1){
                FeatureTemplate[] templates = sightings.getTemplates();
                if (templates == null || templates.length < 1){
                    newSighting = new Graphic(userLocation, null);
                } else {
                    newSighting = sightings.createFeatureWithTemplate(templates[0], userLocation);
                }
            } else {
                newSighting = sightings.createFeatureWithType(featuretypes[0], userLocation);
            }

            // Instantiate a PopupContainer
            popupContainer = new PopupContainer(map);
            // Add Popup
            Popup popup = sightings.createPopup(map, 0, newSighting);
            popup.setEditMode(true);
            popupContainer.addPopup(popup);
            createEditorBar(sightings, false);

            // Create a dialog for the popups and display it.
            popupDialog = new PopupDialog(map.getContext(),
                    popupContainer);
            popupDialog.show();
        }
    }

    // handle edits
    private class EditCallbackListener implements
            CallbackListener<FeatureEditResult[][]> {
        private String operation = "Operation ";
        private ArcGISFeatureLayer featureLayer = null;
        private boolean existingFeature = true;

        public EditCallbackListener(String msg,
                                    ArcGISFeatureLayer featureLayer, boolean existingFeature) {
            this.operation = msg;
            this.featureLayer = featureLayer;
            this.existingFeature = existingFeature;
        }

        @Override
        public void onCallback(FeatureEditResult[][] objs) {
            if (featureLayer == null || !featureLayer.isInitialized()
                    || !featureLayer.isEditable())
                return;

            getActivity().runOnUiThread(new Runnable() {

                @Override
                public void run() {
                    Toast.makeText(getContext(),
                            operation + " succeeded!", Toast.LENGTH_SHORT)
                            .show();
                }
            });

            if (objs[1] == null || objs[1].length <= 0) {
                // Save attachments to the server if newly added attachments
                // exist.
                // Retrieve object id of the feature
                long oid;
                if (existingFeature) {
                    oid = objs[2][0].getObjectId();
                } else {
                    oid = objs[0][0].getObjectId();
                }
                // prepare oid as int for FeatureLayer
                int objectID = (int) oid;
                // Get newly added attachments
                List<File> attachments = popupContainer.getCurrentPopup()
                        .getAddedAttachments();
                if (attachments != null && attachments.size() > 0) {
                    for (File attachment : attachments) {
                        // Save newly added attachment based on the object id of
                        // the feature.
                        featureLayer.addAttachment(objectID, attachment,
                                new CallbackListener<FeatureEditResult>() {
                                    @Override
                                    public void onError(Throwable e) {
                                        // Failed to save new attachments.
                                        getActivity().runOnUiThread(new Runnable() {
                                            @Override
                                            public void run() {
                                                Toast.makeText(
                                                        getContext(),
                                                        "Adding attachment failed!",
                                                        Toast.LENGTH_SHORT)
                                                        .show();
                                            }
                                        });
                                    }

                                    @Override
                                    public void onCallback(
                                            FeatureEditResult arg0) {
                                        // New attachments have been saved.
                                        getActivity().runOnUiThread(new Runnable() {
                                            @Override
                                            public void run() {
                                                Toast.makeText(
                                                        getContext(),
                                                        "Adding attachment succeeded!.",
                                                        Toast.LENGTH_SHORT)
                                                        .show();
                                            }
                                        });
                                    }
                                });
                    }
                }

                // Delete attachments if some attachments have been mark as
                // delete.
                // Get ids of attachments which are marked as delete.
                List<Integer> attachmentIDs = popupContainer.getCurrentPopup()
                        .getDeletedAttachmentIDs();
                if (attachmentIDs != null && attachmentIDs.size() > 0) {
                    int[] ids = new int[attachmentIDs.size()];
                    for (int i = 0; i < attachmentIDs.size(); i++) {
                        ids[i] = attachmentIDs.get(i);
                    }
                    // Delete attachments
                    featureLayer.deleteAttachments(objectID, ids,
                            new CallbackListener<FeatureEditResult[]>() {
                                @Override
                                public void onError(Throwable e) {
                                    // Failed to delete attachments
                                    getActivity().runOnUiThread(new Runnable() {
                                        @Override
                                        public void run() {
                                            Toast.makeText(
                                                    getContext(),
                                                    "Deleting attachment failed!",
                                                    Toast.LENGTH_SHORT).show();
                                        }
                                    });
                                }

                                @Override
                                public void onCallback(FeatureEditResult[] featureEditResults) {
                                    // Attachments have been removed.
                                    getActivity().runOnUiThread(new Runnable() {
                                        @Override
                                        public void run() {
                                            Toast.makeText(
                                                    getContext(),
                                                    "Deleting attachment succeeded!",
                                                    Toast.LENGTH_SHORT).show();
                                        }
                                    });
                                }
                            });
                }

            }
        }

        @Override
        public void onError(Throwable e) {
            getActivity().runOnUiThread(new Runnable() {

                @Override
                public void run() {
                    Toast.makeText(getContext(),
                            operation + " failed!", Toast.LENGTH_SHORT).show();
                }
            });
        }

    }

    private void createEditorBar(final ArcGISFeatureLayer fl,
                                 final boolean existing) {
        if (fl == null || !fl.isInitialized() || !fl.isEditable())
            return;

        editorBar = new LinearLayout(getContext());

        Button cancelButton = new Button(getContext());
        cancelButton.setText("Cancel");
        cancelButton.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {
                if (popupDialog != null)
                    popupDialog.dismiss();
            }
        });
        editorBar.addView(cancelButton);

        final Button deleteButton = new Button(getContext());
        deleteButton.setText("Delete");
        deleteButton.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {
                if (popupContainer == null
                        || popupContainer.getPopupCount() <= 0)
                    return;
                popupDialog.dismiss();

                Feature fr = popupContainer.getCurrentPopup().getFeature();
                Graphic gr = new Graphic(fr.getGeometry(), fr.getSymbol(), fr.getAttributes());
                fl.applyEdits(null, new Graphic[] { gr }, null,
                        new EditCallbackListener("Deleting feature", fl,
                                existing));

            }
        });
        if (existing)
            editorBar.addView(deleteButton);

        final Button attachmentButton = new Button(getContext());
        attachmentButton.setText("Add Attachment");
        attachmentButton.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {

                startActivityForResult(new Intent(Intent.ACTION_PICK,
                        MediaStore.Images.Media.INTERNAL_CONTENT_URI), 1);

                /*
                Intent takePictureIntent = new Intent(Intent.ACTION_PICK, MediaStore.ACTION_IMAGE_CAPTURE);
                */
                //MapFragment.this.startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE);

            }
        });
        if (!existing && fl.hasAttachments())
            attachmentButton.setVisibility(View.VISIBLE);
        else
            attachmentButton.setVisibility(View.INVISIBLE);
        editorBar.addView(attachmentButton);

        final Button saveButton = new Button(getContext());
        saveButton.setText("Save");
        if (existing)
            saveButton.setVisibility(View.INVISIBLE);
        saveButton.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {
                if (popupContainer == null
                        || popupContainer.getPopupCount() <= 0)
                    return;
                popupDialog.dismiss();

                Popup popup = popupContainer.getCurrentPopup();
                Feature fr = popup.getFeature();
                Map<String, Object> attributes = fr.getAttributes();
                Map<String, Object> updatedAttrs = popup.getUpdatedAttributes();
                for (Entry<String, Object> entry : updatedAttrs.entrySet()) {
                    attributes.put(entry.getKey(), entry.getValue());
                }
                Graphic newgr = new Graphic(fr.getGeometry(), null, attributes);
                if (existing)
                    fl.applyEdits(null, null, new Graphic[] { newgr },
                            new EditCallbackListener("Saving edits", fl,
                                    existing));
                else
                    fl.applyEdits(new Graphic[] { newgr }, null, null,
                            new EditCallbackListener("Creating new feature",
                                    fl, existing));
            }
        });
        editorBar.addView(saveButton);

        final Button editButton = new Button(map.getContext());
        editButton.setText("Edit");
        editButton.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {
                if (popupContainer == null
                        || popupContainer.getPopupCount() <= 0)
                    return;

                popupContainer.getCurrentPopup().setEditMode(true);
                saveButton.setVisibility(View.VISIBLE);
                deleteButton.setVisibility(View.INVISIBLE);
                editButton.setVisibility(View.INVISIBLE);
                if (fl.hasAttachments())
                    attachmentButton.setVisibility(View.VISIBLE);
            }
        });
        if (existing) {
            editorBar.addView(editButton);
        }

        popupContainer.getPopupContainerView().addView(editorBar, 0);

    }


    // A customize full screen dialog.
    private class PopupDialog extends Dialog {
        private PopupContainer pContainer;

        public PopupDialog(Context context, PopupContainer popupContainer) {
            super(context, android.R.style.Theme);
            this.pContainer = popupContainer;
        }

        @Override
        protected void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            LayoutParams params = new LayoutParams(LayoutParams.WRAP_CONTENT,
                    LayoutParams.WRAP_CONTENT);
            LinearLayout layout = new LinearLayout(getContext());
            layout.addView(pContainer.getPopupContainerView(),
                    LayoutParams.MATCH_PARENT,
                    LayoutParams.MATCH_PARENT);
            setContentView(layout, params);
        }

    }

}
