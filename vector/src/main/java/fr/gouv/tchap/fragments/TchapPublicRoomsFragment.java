/*
 * Copyright 2018 New Vector Ltd
 * Copyright 2018 DINSIC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package fr.gouv.tchap.fragments;

import android.os.Bundle;
import android.support.v4.content.ContextCompat;
import android.support.v7.widget.DividerItemDecoration;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Filter;
import android.widget.Toast;

import org.matrix.androidsdk.data.Room;
import org.matrix.androidsdk.data.RoomPreviewData;
import org.matrix.androidsdk.rest.callback.ApiCallback;
import org.matrix.androidsdk.rest.model.MatrixError;
import org.matrix.androidsdk.rest.model.publicroom.PublicRoom;
import org.matrix.androidsdk.util.Log;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import butterknife.BindView;
import im.vector.PublicRoomsManager;
import im.vector.R;
import im.vector.activity.CommonActivityUtils;
import im.vector.activity.VectorRoomActivity;
import im.vector.adapters.AdapterSection;
import im.vector.fragments.AbsHomeFragment;
import im.vector.view.EmptyViewItemDecoration;
import im.vector.view.SectionView;
import im.vector.view.SimpleDividerItemDecoration;
import fr.gouv.tchap.adapters.TchapPublicRoomAdapter;

public class TchapPublicRoomsFragment extends AbsHomeFragment {
    private static final String LOG_TAG = TchapPublicRoomsFragment.class.getSimpleName();

    private boolean mMorePublicRooms = false;
    @BindView(R.id.recyclerview)
    RecyclerView mRecycler;

    // rooms management
    private TchapPublicRoomAdapter mAdapter;

    private  List<String> mCurrentHosts = null;
    private List<PublicRoomsManager> mPublicRoomsManagers = null;
    // rooms list
    private final List<Room> mRooms = new ArrayList<>();

    /*
     * *********************************************************************************************
     * Static methods
     * *********************************************************************************************
     */

    public static TchapPublicRoomsFragment newInstance() {
        return new TchapPublicRoomsFragment();
    }

    /*
     * *********************************************************************************************
     * Fragment lifecycle
     * *********************************************************************************************
     */

    @Override
    public View onCreateView(final LayoutInflater inflater, final ViewGroup container, final Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_rooms, container, false);
    }

    @Override
    public void onActivityCreated(final Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        mPrimaryColor = ContextCompat.getColor(getActivity(), R.color.tab_rooms);
        mSecondaryColor = ContextCompat.getColor(getActivity(), R.color.tab_rooms_secondary);
        String userHSName = mSession.getMyUserId().substring(mSession.getMyUserId().indexOf(":") + 1);
        List<String> servers = Arrays.asList(getResources().getStringArray(R.array.room_directory_servers));
        mCurrentHosts = new ArrayList<>();
        boolean isUserHSNameAdded = false;
        for (int i=0;i<servers.size();i++) {
            if (servers.get(i).compareTo(userHSName) == 0) {
                mCurrentHosts.add(null);
                isUserHSNameAdded = true;
            }
            else {
                mCurrentHosts.add(servers.get(i));
            }
        }
        if (!isUserHSNameAdded) {
            mCurrentHosts.add(null);
        }
        initViews();


        mAdapter.onFilterDone(mCurrentFilter);

        initPublicRooms(false);
    }

    @Override
    public void onResume() {
        super.onResume();

        if (null != mActivity) {
            mAdapter.setInvitation(mActivity.getRoomInvitations());
        }
        mRecycler.addOnScrollListener(mScrollListener);
    }

    @Override
    public void onPause() {
        super.onPause();
        mRecycler.removeOnScrollListener(mScrollListener);
    }

    /*
     * *********************************************************************************************
     * Abstract methods implementation
     * *********************************************************************************************
     */

    @Override
    protected List<Room> getRooms() {
        return new ArrayList<>(mRooms);
    }

    @Override
    protected void onFilter(String pattern, final OnFilterListener listener) {
        mAdapter.getFilter().filter(pattern, new Filter.FilterListener() {
            @Override
            public void onFilterComplete(int count) {
                Log.i(LOG_TAG, "onFilterComplete " + count);
                if (listener != null) {
                    listener.onFilterDone(count);
                }

                // trigger the public rooms search to avoid unexpected list refresh
                initPublicRooms(false);
            }
        });
    }

    @Override
    protected void onResetFilter() {
        mAdapter.getFilter().filter("", new Filter.FilterListener() {
            @Override
            public void onFilterComplete(int count) {
                Log.i(LOG_TAG, "onResetFilter " + count);

                // trigger the public rooms search to avoid unexpected list refresh
                initPublicRooms(false);
            }
        });
    }

    /*
     * *********************************************************************************************
     * Public methods
     * *********************************************************************************************
     */


    /*
     * *********************************************************************************************
     * UI management
     * *********************************************************************************************
     */

    private void initViews() {
        int margin = (int) getResources().getDimension(R.dimen.item_decoration_left_margin);
        mRecycler.setLayoutManager(new LinearLayoutManager(getActivity(), LinearLayoutManager.VERTICAL, false));
        mRecycler.addItemDecoration(new SimpleDividerItemDecoration(getActivity(), DividerItemDecoration.VERTICAL, margin));
        mRecycler.addItemDecoration(new EmptyViewItemDecoration(getActivity(), DividerItemDecoration.VERTICAL, 40, 16, 14));

        mAdapter = new TchapPublicRoomAdapter(getActivity(), new TchapPublicRoomAdapter.OnSelectItemListener() {
            @Override
            public void onSelectItem(Room room, int position) {
                openRoom(room);
            }

            @Override
            public void onSelectItem(PublicRoom publicRoom) {
                onPublicRoomSelected(publicRoom);
            }
        }, this, this);
        mRecycler.setAdapter(mAdapter);
    }

    /*
     * *********************************************************************************************
     * Public rooms management
     * *********************************************************************************************
     */

    // spinner text
    private ArrayAdapter<CharSequence> mRoomDirectoryAdapter;

    /**
     * Handle a public room selection
     *
     * @param publicRoom the public room
     */
    private void onPublicRoomSelected(final PublicRoom publicRoom) {
        // sanity check
        if (null != publicRoom.roomId) {
            final RoomPreviewData roomPreviewData = new RoomPreviewData(mSession, publicRoom.roomId, null, publicRoom.getAlias(), null);

            Room room = mSession.getDataHandler().getRoom(publicRoom.roomId, false);

            // if the room exists
            if (null != room) {
                // either the user is invited
                if (room.isInvited()) {
                    Log.d(LOG_TAG, "manageRoom : the user is invited -> display the preview " + getActivity());
                    CommonActivityUtils.previewRoom(getActivity(), roomPreviewData);
                } else {
                    Log.d(LOG_TAG, "manageRoom : open the room");
                    HashMap<String, Object> params = new HashMap<>();
                    params.put(VectorRoomActivity.EXTRA_MATRIX_ID, mSession.getMyUserId());
                    params.put(VectorRoomActivity.EXTRA_ROOM_ID, publicRoom.roomId);

                    if (!TextUtils.isEmpty(publicRoom.name)) {
                        params.put(VectorRoomActivity.EXTRA_DEFAULT_NAME, publicRoom.name);
                    }

                    if (!TextUtils.isEmpty(publicRoom.topic)) {
                        params.put(VectorRoomActivity.EXTRA_DEFAULT_TOPIC, publicRoom.topic);
                    }

                    CommonActivityUtils.goToRoomPage(getActivity(), mSession, params);
                }
            } else {
                if (null != mActivity) {
                    mActivity.showWaitingView();
                }

                roomPreviewData.fetchPreviewData(new ApiCallback<Void>() {
                    private void onDone() {
                        if (null != mActivity) {
                            mActivity.hideWaitingView();
                        }
                        CommonActivityUtils.previewRoom(getActivity(), roomPreviewData);
                    }

                    @Override
                    public void onSuccess(Void info) {
                        onDone();
                    }

                    private void onError() {
                        roomPreviewData.setRoomState(publicRoom);
                        roomPreviewData.setRoomName(publicRoom.name);
                        onDone();
                    }

                    @Override
                    public void onNetworkError(Exception e) {
                        onError();
                    }

                    @Override
                    public void onMatrixError(MatrixError e) {
                        if (MatrixError.M_CONSENT_NOT_GIVEN.equals(e.errcode) && isAdded()) {
                            mActivity.getConsentNotGivenHelper().displayDialog(e);
                        } else {
                            onError();
                        }
                    }

                    @Override
                    public void onUnexpectedError(Exception e) {
                        onError();
                    }
                });
            }
        }
    }

    /**
     * Scroll events listener to forward paginate when it is required.
     */
    private final RecyclerView.OnScrollListener mPublicRoomScrollListener = new RecyclerView.OnScrollListener() {
        @Override
        public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
            super.onScrolled(recyclerView, dx, dy);
            LinearLayoutManager layoutManager = (LinearLayoutManager) mRecycler.getLayoutManager();
            int lastVisibleItemPosition = layoutManager.findLastCompletelyVisibleItemPosition();

            // we load public rooms 20 by 20, when the 10th one becomes visible, starts loading the next 20
            SectionView sectionView = mAdapter.getSectionViewForSectionIndex(mAdapter.getSectionsCount() - 1);
            AdapterSection lastSection = sectionView != null ? sectionView.getSection() : null;

            if (null != lastSection) {
                // detect if the last visible item is inside another section
                for (int i = 0; i < mAdapter.getSectionsCount() - 1; i++) {
                    SectionView prevSectionView = mAdapter.getSectionViewForSectionIndex(i);

                    if ((null != prevSectionView) && (null != prevSectionView.getSection())) {
                        lastVisibleItemPosition -= prevSectionView.getSection().getNbItems();

                        // the item is in a previous section
                        if (lastVisibleItemPosition <= 0) {
                            return;
                        }
                    }
                }

                // trigger a forward paginate when there are only 10 items left
                if ((lastSection.getNbItems() - lastVisibleItemPosition) < 10) {
                    forwardPaginate();
                }
            }
        }
    };

    /**
     * Display the public rooms loading view
     */
    private void showPublicRoomsLoadingView() {
        if (mAdapter.getSectionViewForSectionIndex(mAdapter.getSectionsCount() - 1) != null) {
            mAdapter.getSectionViewForSectionIndex(mAdapter.getSectionsCount() - 1).showLoadingView();
        }
    }

    /**
     * Hide the public rooms loading view
     */
    private void hidePublicRoomsLoadingView() {
        if (mAdapter.getSectionViewForSectionIndex(mAdapter.getSectionsCount() - 1) != null) {
            mAdapter.getSectionViewForSectionIndex(mAdapter.getSectionsCount() - 1).hideLoadingView();
        }
    }

    /**
     * Init the public rooms.
     *
     * @param displayOnTop true to display the public rooms in full screen
     */
    private void initPublicRooms(final boolean displayOnTop) {
        showPublicRoomsLoadingView();

        mAdapter.setNoMorePublicRooms(false);
        initPublicRoomsCascade(displayOnTop, 0);
    }

    private void initPublicRoomsCascade(final boolean displayOnTop, final int hostIndex) {
        if (hostIndex == 0) {
            mAdapter.setNoMorePublicRooms(false);
            mAdapter.setPublicRooms(null);
            mMorePublicRooms = false;
            showPublicRoomsLoadingView();
        }

        if (mPublicRoomsManagers == null) {
            mPublicRoomsManagers = new ArrayList<>();
            initPublicRoomsManagers();
        }

        PublicRoomsManager myPRM = mPublicRoomsManagers.get(hostIndex);
        myPRM.startPublicRoomsSearch(mCurrentHosts.get(hostIndex),
                null,
                false,
                mCurrentFilter, new ApiCallback<List<PublicRoom>>() {
                    @Override
                    public void onSuccess(List<PublicRoom> publicRooms) {
                        if (null != getActivity()) {
                            mMorePublicRooms = mMorePublicRooms || (publicRooms.size() >= PublicRoomsManager.PUBLIC_ROOMS_LIMIT);
                            mAdapter.addPublicRooms(publicRooms);
                            if (hostIndex == mCurrentHosts.size()-1) {
                                mAdapter.setNoMorePublicRooms(!mMorePublicRooms);
                                addPublicRoomsListener();

                                // trick to display the full public rooms list
                                if (displayOnTop) {
                                    // wait that the list is refreshed
                                    mRecycler.post(new Runnable() {
                                        @Override
                                        public void run() {
                                            SectionView publicSectionView = mAdapter.getSectionViewForSectionIndex(mAdapter.getSectionsCount() - 1);

                                            // simulate a click on the header is to display the full list
                                            if ((null != publicSectionView) && !publicSectionView.isStickyHeader()) {
                                                publicSectionView.callOnClick();
                                            }
                                        }
                                    });
                                }

                                hidePublicRoomsLoadingView();

                            }
                            else {
                                initPublicRoomsCascade(displayOnTop, hostIndex+1);
                            }
                        }
                    }

                    private void onError(String message) {
                        if (null != getActivity()) {
                            Log.e(LOG_TAG, "## startPublicRoomsSearch() failed " + message);
                            // Pb here when a lot of federation doesn't work, a lot of messages make a crash
                            //    Toast.makeText(getActivity(), message, Toast.LENGTH_SHORT).show();
                            if (hostIndex == mCurrentHosts.size()-1) {
                                mAdapter.setNoMorePublicRooms(!mMorePublicRooms);
                                addPublicRoomsListener();
                                hidePublicRoomsLoadingView();
                            }
                            else {
                                initPublicRoomsCascade(displayOnTop, hostIndex+1);
                            }
                        }
                    }

                    @Override
                    public void onNetworkError(Exception e) {
                        onError(e.getLocalizedMessage());
                    }

                    @Override
                    public void onMatrixError(MatrixError e) {
                        if (MatrixError.M_CONSENT_NOT_GIVEN.equals(e.errcode) && isAdded()) {
                            hidePublicRoomsLoadingView();
                            mActivity.getConsentNotGivenHelper().displayDialog(e);
                        } else {
                            onError(e.getLocalizedMessage());
                        }
                    }

                    @Override
                    public void onUnexpectedError(Exception e) {
                        onError(e.getLocalizedMessage());
                    }
                });
    }
    /**
     * Initialize Public rooms managers
     */
    private void initPublicRoomsManagers() {
        for (int i=0;i<mCurrentHosts.size();i++) {
            PublicRoomsManager myPRM = new PublicRoomsManager();
            myPRM.setSession(mSession);
            mPublicRoomsManagers.add(myPRM);
        }
    }

    /**
     * Trigger a forward room pagination
     */
    private void forwardPaginate() {
        for (int i = 0; i < mPublicRoomsManagers.size(); i++)
            if (mPublicRoomsManagers.get(i).isRequestInProgress()) {
                return;
            }
        mMorePublicRooms = false;
        showPublicRoomsLoadingView();
        cascadeForwardPaginate(0);
    }

    private void cascadeForwardPaginate(final int hostIndex) {

        boolean isForwarding = mPublicRoomsManagers.get(hostIndex).forwardPaginate(new ApiCallback<List<PublicRoom>>() {
            @Override
            public void onSuccess(final List<PublicRoom> publicRooms) {
                if (null != getActivity()) {
                    // unplug the scroll listener if there is no more data to find
                    if (PublicRoomsManager.getInstance().hasMoreResults()) {
                        mMorePublicRooms = true;
                    }
                    mAdapter.addPublicRooms(publicRooms);
                }
                if (hostIndex == mCurrentHosts.size()-1) {
                    hidePublicRoomsLoadingView();
                    if (!mMorePublicRooms) {
                        mAdapter.setNoMorePublicRooms(true);
                        removePublicRoomsListener();
                    }
                }
                else {
                    cascadeForwardPaginate(hostIndex+1);
                }
            }

            private void onError(String message) {
                if (null != getActivity()) {
                    Log.e(LOG_TAG, "## forwardPaginate() failed " + message);
                    Toast.makeText(getActivity(), message, Toast.LENGTH_SHORT).show();
                }
                if (hostIndex == mCurrentHosts.size()) {
                    hidePublicRoomsLoadingView();
                }
                else {
                    cascadeForwardPaginate(hostIndex+1);
                }
            }

            @Override
            public void onNetworkError(Exception e) {
                onError(e.getLocalizedMessage());
            }

            @Override
            public void onMatrixError(MatrixError e) {
                onError(e.getLocalizedMessage());
            }

            @Override
            public void onUnexpectedError(Exception e) {
                onError(e.getLocalizedMessage());
            }
        });
        if (!isForwarding)
            hidePublicRoomsLoadingView();

    }

    /**
     * Add the public rooms listener
     */
    private void addPublicRoomsListener() {
        mRecycler.addOnScrollListener(mPublicRoomScrollListener);
    }

    /**
     * Remove the public rooms listener
     */
    private void removePublicRoomsListener() {
        mRecycler.removeOnScrollListener(null);
    }

}
