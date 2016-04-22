package com.team1.android.eabtracker;


import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v4.widget.TextViewCompat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ExpandableListView;
import android.widget.MediaController;
import android.widget.TextView;
import android.widget.VideoView;

import java.util.ArrayList;
import java.util.List;

public class EABInfoFragment extends Fragment {


    public EABInfoFragment() {
        // Required empty public constructor
    }


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        /*Inflate the layout for this fragment and reference as rootView,
           this will give a View context so that we can access things in the layout_xml
           associated with this Fragment.  for example, if there was a button you needed
           to access in the layout file, you can do it like this from this Fragment:

           deleteButton = (Button) rootView.findViewById(R.id.DeleteButton);
        */
        View rootView = inflater.inflate(R.layout.fragment_eabinfo, container, false);

        /*VideoView videoView = (VideoView) rootView.findViewById(R.id.VideoView);
        MediaController mc = new MediaController(this.getContext());
        mc.setAnchorView(videoView);
        Uri uri = Uri.parse("rtsp://r4---sn-vgqsen7r.googlevideo.com/Cj0LENy73wIaNAmH-KaxaIQDWBMYDSANFC1l_RdXMOCoAUIASARgqKaqoqXIyYFXigELWk0zSGpqRDZhMGMM/5A38524F36FE7ABC39DCB41CC30439F40AFDFB6B.BDEB5B85901DA6C91FBEA8655085F70D6105C76C/yt6/1/video.3gp");
        videoView.setMediaController(mc);
        videoView.setVideoURI(uri);*/
        //videoView.requestFocus();
        //videoView.start();

        return rootView;

    }
}