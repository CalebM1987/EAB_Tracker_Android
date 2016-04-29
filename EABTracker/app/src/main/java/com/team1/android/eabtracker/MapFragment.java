package com.team1.android.eabtracker;


import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.location.Address;
import android.location.Geocoder;
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
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.pm.ActivityInfo;


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
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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
    private Point userLocation;

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

        // Tap on the map and show popups for selected features.
        map.setOnSingleTapListener(new OnSingleTapListener() {
            private static final long serialVersionUID = 1L;

            @Override
            public void onSingleTap(float x, float y) {
                if (map.isLoaded()) {
                    // Instantiate a PopupContainer
                    popupContainer = new PopupContainer(map);
                    int id = popupContainer.hashCode();
                    popupDialog = null;
                    // Display spinner.
                    if (progressDialog == null || !progressDialog.isShowing())
                        progressDialog = ProgressDialog.show(map.getContext(),
                                "", "Querying...");

                    // Loop through each layer in the webmap
                    int tolerance = 20;
                    Envelope env = new Envelope(map.toMapPoint(x, y), 20 * map
                            .getResolution(), 20 * map.getResolution());
                    Layer[] layers = map.getLayers();
                    count = new AtomicInteger();
                    for (Layer layer : layers) {
                        // If the layer has not been initialized or is
                        // invisible, do nothing.
                        if (!layer.isInitialized() || !layer.isVisible())
                            continue;

                        if (layer instanceof ArcGISFeatureLayer) {
                            // Query feature layer and display popups
                            ArcGISFeatureLayer featureLayer = (ArcGISFeatureLayer) layer;
                            if (featureLayer.getPopupInfo() != null) {

                                // Query feature layer which is associated with
                                // a popup definition.
                                count.incrementAndGet();
                                new RunQueryFeatureLayerTask(x, y, tolerance,
                                        id).execute(featureLayer);
                            }
                        } else if (layer instanceof ArcGISDynamicMapServiceLayer) {
                            // Query dynamic map service layer and display
                            // popups.
                            ArcGISDynamicMapServiceLayer dynamicLayer = (ArcGISDynamicMapServiceLayer) layer;
                            // Retrieve layer info for each sub-layer of the
                            // dynamic map service layer.
                            ArcGISLayerInfo[] layerinfos = dynamicLayer
                                    .getAllLayers();
                            if (layerinfos == null)
                                continue;

                            // Loop through each sub-layer
                            for (ArcGISLayerInfo layerInfo : layerinfos) {
                                // Obtain PopupInfo for sub-layer.
                                PopupInfo popupInfo = dynamicLayer
                                        .getPopupInfo(layerInfo.getId());
                                // Skip sub-layer which is without a popup
                                // definition.
                                if (popupInfo == null) {
                                    continue;
                                }
                                // Check if a sub-layer is visible.
                                ArcGISLayerInfo info = layerInfo;
                                while (info != null && info.isVisible()) {
                                    info = info.getParentLayer();
                                }
                                // Skip invisible sub-layer
                                if (info != null && !info.isVisible()) {
                                    continue;
                                }

                                // Check if the sub-layer is within the scale
                                // range
                                double maxScale = (layerInfo.getMaxScale() != 0) ? layerInfo
                                        .getMaxScale() : popupInfo
                                        .getMaxScale();
                                double minScale = (layerInfo.getMinScale() != 0) ? layerInfo
                                        .getMinScale() : popupInfo
                                        .getMinScale();

                                if ((maxScale == 0 || map.getScale() > maxScale)
                                        && (minScale == 0 || map.getScale() < minScale)) {
                                    // Query sub-layer which is associated with
                                    // a popup definition and is visible and in
                                    // scale range.
                                    count.incrementAndGet();
                                    new RunQueryDynamicLayerTask(env, layer,
                                            layerInfo.getId(), dynamicLayer
                                            .getSpatialReference(), id)
                                            .execute(dynamicLayer.getUrl()
                                                    + "/" + layerInfo.getId());
                                }
                            }
                        }
                    }
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
            userLocation = locationManager.getPoint();

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

    // Query feature layer by hit test
    private class RunQueryFeatureLayerTask extends
            AsyncTask<ArcGISFeatureLayer, Void, Feature[]> {

        private int tolerance;
        private float x;
        private float y;
        private ArcGISFeatureLayer featureLayer;
        private int id;

        public RunQueryFeatureLayerTask(float x, float y, int tolerance, int id) {
            super();
            this.x = x;
            this.y = y;
            this.tolerance = tolerance;
            this.id = id;
        }

        @Override
        protected Feature[] doInBackground(ArcGISFeatureLayer... params) {
            for (ArcGISFeatureLayer fLayer : params) {
                this.featureLayer = fLayer;
                // Retrieve feature ids near the point.
                int[] ids = fLayer.getGraphicIDs(x, y, tolerance);
                if (ids != null && ids.length > 0) {
                    ArrayList<Feature> features = new ArrayList<Feature>();
                    for (int graphicId : ids) {
                        // Obtain feature based on the id.
                        Feature f = fLayer.getGraphic(graphicId);
                        if (f == null)
                            continue;
                        features.add(f);
                    }
                    // Return an array of features near the point.
                    return features.toArray(new Feature[0]);
                }
            }
            return null;
        }

        @Override
        protected void onPostExecute(Feature[] features) {
            count.decrementAndGet();
            // Validate parameter.
            if (features == null || features.length == 0) {
                // Dismiss spinner
                if (progressDialog != null && progressDialog.isShowing()
                        && count.intValue() == 0)
                    progressDialog.dismiss();

                return;
            }
            // Check if the requested PopupContainer id is the same as the
            // current PopupContainer.
            // Otherwise, abandon the obsoleted query result.
            if (id != popupContainer.hashCode()) {
                // Dismiss spinner
                if (progressDialog != null && progressDialog.isShowing()
                        && count.intValue() == 0)
                    progressDialog.dismiss();

                return;
            }

            PopupInfo popupInfo = featureLayer.getPopupInfo();
            if (popupInfo == null) {
                // Dismiss spinner
                if (progressDialog != null && progressDialog.isShowing()
                        && count.intValue() == 0)
                    progressDialog.dismiss();

                return;
            }

            for (Feature fr : features) {
                Popup popup = featureLayer.createPopup(map, 0, fr);
                popupContainer.addPopup(popup);
            }
            if (!featureLayer.getName().equals("County Boundary")) {
                createEditorBar(featureLayer, true);
            }
            createPopupViews(id);
        }

    }

    // popup views
    private void createPopupViews(final int id) {
        if (id != popupContainer.hashCode()) {
            if (progressDialog != null && progressDialog.isShowing()
                    && count.intValue() == 0)
                progressDialog.dismiss();

            return;
        }

        if (popupDialog == null) {
            if (progressDialog != null && progressDialog.isShowing())
                progressDialog.dismiss();

            // Create a dialog for the popups and display it.
            popupDialog = new PopupDialog(map.getContext(), popupContainer);
            popupDialog.show();
        }
    }


    // Query dynamic map service layer by QueryTask
    private class RunQueryDynamicLayerTask extends
            AsyncTask<String, Void, FeatureSet> {
        private Envelope env;
        private SpatialReference sr;
        private int id;
        private Layer layer;
        private int subLayerId;

        public RunQueryDynamicLayerTask(Envelope env, Layer layer,
                                        int subLayerId, SpatialReference sr, int id) {
            super();
            this.env = env;
            this.sr = sr;
            this.id = id;
            this.layer = layer;
            this.subLayerId = subLayerId;
        }

        @Override
        protected FeatureSet doInBackground(String... urls) {
            for (String url : urls) {
                // Retrieve features within the envelope.
                Query query = new Query();
                query.setInSpatialReference(sr);
                query.setOutSpatialReference(sr);
                query.setGeometry(env);
                query.setMaxFeatures(10);
                query.setOutFields(new String[] { "*" });

                QueryTask queryTask = new QueryTask(url);
                try {
                    FeatureSet results = queryTask.execute(query);
                    return results;
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            return null;
        }

        @Override
        protected void onPostExecute(final FeatureSet result) {
            // Validate parameter.
            count.decrementAndGet();
            if (result == null) {
                // Dismiss spinner
                if (progressDialog != null && progressDialog.isShowing()
                        && count.intValue() == 0)
                    progressDialog.dismiss();

                return;
            }
            Feature[] features = result.getGraphics();
            if (features == null || features.length == 0) {
                // Dismiss spinner
                if (progressDialog != null && progressDialog.isShowing()
                        && count.intValue() == 0)
                    progressDialog.dismiss();

                return;
            }
            // Check if the requested PopupContainer id is the same as the
            // current PopupContainer.
            // Otherwise, abandon the obsoleted query result.
            if (id != popupContainer.hashCode()) {
                // Dismiss spinner
                if (progressDialog != null && progressDialog.isShowing()
                        && count.intValue() == 0)
                    progressDialog.dismiss();

                return;
            }

            for (Feature fr : features) {
                Popup popup = layer.createPopup(map, subLayerId, fr);
                popupContainer.addPopup(popup);
            }
            createPopupViews(id);

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
                Log.d("attachments", "" + attachments.size());
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

                if (!fl.getName().equals("County Boundary")) {

                    Feature fr = popupContainer.getCurrentPopup().getFeature();
                    Graphic gr = new Graphic(fr.getGeometry(), fr.getSymbol(), fr.getAttributes());

                    fl.applyEdits(null, new Graphic[]{gr}, null,
                            new EditCallbackListener("Deleting feature", fl,
                                    existing));
                }

            }
        });
        if (existing)
            editorBar.addView(deleteButton);

        final Button attachmentButton = new Button(getContext());
        attachmentButton.setText("Add Photo");
        attachmentButton.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {
                // allow user to choose photo or take photo
                selectImage();

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

                // get user location as WGS 84
                Point locWGS = (Point) GeometryEngine.project(userLocation, map.getSpatialReference(), wgsSR);
                Map<String, Object> attributes = fr.getAttributes();
                Map<String, Object> updatedAttrs = popup.getUpdatedAttributes();
                for (Entry<String, Object> entry : updatedAttrs.entrySet()) {

                    attributes.put(entry.getKey(), entry.getValue());

                }

                // add lat/long and address
                attributes.put("Latitude", locWGS.getY());
                attributes.put("Longitude", locWGS.getX());
                attributes.put("Site_Address", getAddress(locWGS.getY(), locWGS.getX()));

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

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {

        if (resultCode == getActivity().RESULT_OK && data != null
                && popupContainer != null) {
            // Add the selected media as attachment.
            Uri selectedImage = data.getData();
            popupContainer.getCurrentPopup().addAttachment(selectedImage);
            getActivity().setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        }
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

    private void selectImage() {
        final CharSequence[] items = { "Take Photo", "Choose from Library", "Cancel" };
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setTitle("Add Photo!");
        builder.setItems(items, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int item) {
                if (items[item].equals("Take Photo")) {
                    Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                    startActivityForResult(intent, 1);
                } else if (items[item].equals("Choose from Library")) {
                    Intent intent = new Intent(
                            Intent.ACTION_PICK,
                            android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
                    intent.setType("image/*");
                    startActivityForResult(
                            Intent.createChooser(intent, "Select File"),
                            1);
                } else if (items[item].equals("Cancel")) {
                    dialog.dismiss();
                }
            }
        });
        builder.show();
    }

    public String getAddress(Double lat, Double lon) {

        Geocoder geocoder = new Geocoder(getActivity(), Locale.ENGLISH);
        StringBuilder strAddress = new StringBuilder();

        try {

            //Place your latitude and longitude
            List<Address> addresses = geocoder.getFromLocation(lat, lon, 1);

            if (addresses != null) {

                Address fetchedAddress = addresses.get(0);


                for (int i = 0; i < fetchedAddress.getMaxAddressLineIndex(); i++) {
                    strAddress.append(fetchedAddress.getAddressLine(i) + " ");
                }

            }


        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
            Toast.makeText(getContext(), "Could not get address..!", Toast.LENGTH_LONG).show();
        }
        return strAddress.toString();
    }


}
