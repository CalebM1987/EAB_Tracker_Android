package com.team1.android.eabtracker;


import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;


public class AshInfoFragment extends Fragment {


    public AshInfoFragment() {
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
        View rootView = inflater.inflate(R.layout.fragment_ashinfo, container, false);
        return rootView;

    }


}