package com.namelessdev.mpdroid;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.a0z.mpd.MPDPlaylist;
import org.a0z.mpd.MPDStatus;
import org.a0z.mpd.Music;
import org.a0z.mpd.event.StatusChangeListener;
import org.a0z.mpd.exception.MPDServerException;

import com.namelessdev.mpdroid.tools.Tools;

import android.app.ActionBar;
import android.app.Activity;
import android.app.ListActivity;
import android.content.Intent;
import android.os.Bundle;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.ListView;
import android.widget.SimpleAdapter;

public class PlaylistActivity extends ListActivity implements OnClickListener, StatusChangeListener {
	private ArrayList<HashMap<String, Object>> songlist;
	private List<Music> musics;
	// private int arrayListId;

	private String title;
	private boolean firstUpdate = true;
	private com.namelessdev.mpdroid.ActionBar compatActionBar;
	private MPDApplication app;

	public static final int MAIN = 0;
	public static final int CLEAR = 1;
	public static final int MANAGER = 3;
	public static final int SAVE = 4;
	public static final int EDIT = 2;

	@Override
	public void onCreate(Bundle icicle) {
		if (!Tools.isTabletMode(this)) {
			setTheme(android.R.style.Theme_Black_NoTitleBar);
		}

		super.onCreate(icicle);
		app = (MPDApplication) getApplication();
		setContentView(R.layout.playlist_activity);
		this.setTitle(R.string.nowPlaying);
		ListView list = getListView();
		/*
		 * LinearLayout test = (LinearLayout)list.getChildAt(1); ImageView img = (ImageView)test.findViewById(R.id.picture); //ImageView img
		 * = (ImageView)((LinearLayout)list.getItemAtPosition(3)).findViewById(R.id.picture);
		 * img.setImageDrawable(getResources().getDrawable(R.drawable.gmpcnocover));
		 */

		registerForContextMenu(list);

		try {
			Activity activity = this;
			ActionBar actionBar = activity.getActionBar();
			actionBar.setDisplayHomeAsUpEnabled(true);
		} catch (NoClassDefFoundError e) {
			// Older android
		} catch (NullPointerException e) {

		} catch (NoSuchMethodError e) {

		}

		final View tmpView = findViewById(R.id.compatActionbar);
		if (tmpView != null) {
			// We are on a phone
			compatActionBar = (com.namelessdev.mpdroid.ActionBar) tmpView;
			compatActionBar.setTitle(R.string.nowPlaying);
			compatActionBar.setTextButtonParams(true, R.string.edit, this);
			compatActionBar.setBackActionEnabled(true);
			compatActionBar.showBottomSeparator(true);
		}
	}

	protected void update() {
		MPDApplication app = (MPDApplication) getApplicationContext();
		try {
			MPDPlaylist playlist = app.oMPDAsyncHelper.oMPD.getPlaylist();
			songlist = new ArrayList<HashMap<String, Object>>();
			musics = playlist.getMusicList();
			int playingID = app.oMPDAsyncHelper.oMPD.getStatus().getSongId();
			// The position in the songlist of the currently played song
			int listPlayingID = -1;
			for (Music m : musics) {
				if (m == null) {
					continue;
				}
				HashMap<String, Object> item = new HashMap<String, Object>();
				item.put("songid", m.getSongId());
				item.put("artist", m.getArtist());
				item.put("title", m.getTitle());
				if (m.getSongId() == playingID) {
					item.put("play", android.R.drawable.ic_media_play);
					// Lie a little. Scroll to the previous song than the one playing. That way it shows that there are other songs before
					// it
					listPlayingID = songlist.size() - 1;
				} else {
					item.put("play", 0);
				}
				songlist.add(item);
			}
			SimpleAdapter songs = new SimpleAdapter(this, songlist, R.layout.playlist_list_item,
					new String[] { "play", "title", "artist" }, new int[] { R.id.picture, android.R.id.text1, android.R.id.text2 });

			setListAdapter(songs);

			// Only scroll if there is a valid song to scroll to. 0 is a valid song but does not require scroll anyway.
			// Also, only scroll if it's the first update. You don't want your playlist to scroll itself while you are looking at other
			// stuff.
			if (firstUpdate && listPlayingID > 0)
				setSelection(listPlayingID);
			firstUpdate = false;

		} catch (MPDServerException e) {
		}

	}

	@Override
	protected void onStart() {
		super.onStart();
		app.setActivity(this);
	}

	@Override
	protected void onResume() {
		super.onResume();
		app.oMPDAsyncHelper.addStatusChangeListener(this);
		update();
	}

	@Override
	protected void onPause() {
		app.oMPDAsyncHelper.addStatusChangeListener(this);
		super.onPause();
	}

	@Override
	protected void onStop() {
		super.onStop();
		app.unsetActivity(this);
	}

	@Override
	public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {

		super.onCreateContextMenu(menu, v, menuInfo);
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.mpd_playlistcnxmenu, menu);

		AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) menuInfo;
		// arrayListId = info.position;

		title = (String) songlist.get(info.position).get("title");
		menu.setHeaderTitle(title);
	}

	@Override
	public boolean onContextItemSelected(MenuItem item) {
		MPDApplication app = (MPDApplication) getApplication();
		AdapterContextMenuInfo info = (AdapterContextMenuInfo) item.getMenuInfo();
		int songId = (Integer) songlist.get(info.position).get("songid");
		switch (item.getItemId()) {
		case R.id.PLCX_SkipToHere:
			// skip to selected Song
			try {
				app.oMPDAsyncHelper.oMPD.skipToId(songId);
			} catch (MPDServerException e) {
			}
			return true;
		case R.id.PLCX_playNext:
			try { // Move song to next in playlist
				MPDStatus status = app.oMPDAsyncHelper.oMPD.getStatus();
				if (info.id < status.getSongPos()) {
					app.oMPDAsyncHelper.oMPD.getPlaylist().move(songId, status.getSongPos());
				} else {
					app.oMPDAsyncHelper.oMPD.getPlaylist().move(songId, status.getSongPos() + 1);
				}
				Tools.notifyUser("Song moved to next in list", this);
			} catch (MPDServerException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			return true;
		case R.id.PLCX_moveFirst:
			try { // Move song to first in playlist
				app.oMPDAsyncHelper.oMPD.getPlaylist().move(songId, 0);
				Tools.notifyUser("Song moved to first in list", this);
			} catch (MPDServerException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			return true;
		case R.id.PLCX_moveLast:
			try { // Move song to last in playlist
				MPDStatus status = app.oMPDAsyncHelper.oMPD.getStatus();
				app.oMPDAsyncHelper.oMPD.getPlaylist().move(songId, status.getPlaylistLength() - 1);
				Tools.notifyUser("Song moved to last in list", this);
			} catch (MPDServerException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			return true;
		case R.id.PLCX_removeFromPlaylist:
			try {
				app.oMPDAsyncHelper.oMPD.getPlaylist().removeById(songId);
				Tools.notifyUser(getResources().getString(R.string.deletedSongFromPlaylist), this);
			} catch (MPDServerException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			return true;
		default:
			return super.onContextItemSelected(item);
		}
	}

	/*
	 * Create Menu for Playlist View
	 * 
	 * @see android.app.Activity#onCreateOptionsMenu(android.view.Menu)
	 */
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		boolean result = super.onCreateOptionsMenu(menu);
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.mpd_playlistmenu, menu);

		return result;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		MPDApplication app = (MPDApplication) getApplication();
		// Menu actions...
		switch (item.getItemId()) {
		case R.id.PLM_MainMenu:
			Intent i = new Intent(this, MainMenuActivity.class);
			i.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
			startActivity(i);
			return true;
		case R.id.PLM_LibTab:
			i = new Intent(this, LibraryTabActivity.class);
			i.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
			startActivity(i);
			return true;
		case R.id.PLM_Clear:
			try {
				app.oMPDAsyncHelper.oMPD.getPlaylist().clear();
				songlist.clear();
				Tools.notifyUser(getResources().getString(R.string.playlistCleared), this);
				((SimpleAdapter) getListAdapter()).notifyDataSetChanged();
			} catch (MPDServerException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			return true;
		case R.id.PLM_EditPL:
			i = new Intent(this, PlaylistRemoveActivity.class);
			startActivity(i);
			return true;
		case R.id.PLM_Manage:
			i = new Intent(this, PlaylistManagerActivity.class);
			startActivity(i);
			return true;
		case R.id.PLM_Save:
			i = new Intent(this, PlaylistSaveActivity.class);
			startActivity(i);
			return true;
		case android.R.id.home:
			finish();
			return true;
		default:
			return false;
		}

	}

	@Override
	protected void onListItemClick(ListView l, View v, int position, long id) {

		MPDApplication app = (MPDApplication) getApplication(); // Play selected Song

		Music m = musics.get(position);
		try {
			app.oMPDAsyncHelper.oMPD.skipToId(m.getSongId());
		} catch (MPDServerException e) {
		}

	}

	public void scrollToNowPlaying() {
		for (HashMap<String, Object> song : songlist) {
			try {
				if (((Integer) song.get("songid")).intValue() == ((MPDApplication) getApplication()).oMPDAsyncHelper.oMPD.getStatus()
						.getSongId()) {
					getListView().requestFocusFromTouch();
					getListView().setSelection(songlist.indexOf(song));
				}
			} catch (MPDServerException e) {
			}
		}
	}

	@Override
	public void onClick(View v) {
		switch (v.getId()) {
		case R.id.actionbar_text:
			Intent i = new Intent(this, PlaylistRemoveActivity.class);
			startActivityForResult(i, EDIT);
			break;
		default:
			break;
		}
	}

	@Override
	public void volumeChanged(MPDStatus mpdStatus, int oldVolume) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void playlistChanged(MPDStatus mpdStatus, int oldPlaylistVersion) {
		update();
		
	}

	@Override
	public void trackChanged(MPDStatus mpdStatus, int oldTrack) {
		// Mark running track...
		for (HashMap<String, Object> song : songlist) {
			if (((Integer) song.get("songid")).intValue() == mpdStatus.getSongId())
				song.put("play", android.R.drawable.ic_media_play);
			else
				song.put("play", 0);

		}
		((SimpleAdapter) getListAdapter()).notifyDataSetChanged();
		
	}

	@Override
	public void stateChanged(MPDStatus mpdStatus, String oldState) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void repeatChanged(boolean repeating) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void randomChanged(boolean random) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void connectionStateChanged(boolean connected, boolean connectionLost) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void libraryStateChanged(boolean updating) {
		// TODO Auto-generated method stub
		
	}

}
