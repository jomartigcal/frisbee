/*
 * Copyright 2013-2015 The GDG Frisbee Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gdg.frisbee.android.widget;

import android.app.PendingIntent;
import android.app.Service;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.net.Uri;
import android.os.IBinder;
import android.widget.RemoteViews;

import com.android.volley.Response;
import com.android.volley.VolleyError;

import org.gdg.frisbee.android.Const;
import org.gdg.frisbee.android.R;
import org.gdg.frisbee.android.activity.MainActivity;
import org.gdg.frisbee.android.api.GroupDirectory;
import org.gdg.frisbee.android.api.model.Chapter;
import org.gdg.frisbee.android.api.model.Directory;
import org.gdg.frisbee.android.api.model.Event;
import org.gdg.frisbee.android.app.App;
import org.gdg.frisbee.android.cache.ModelCache;
import org.gdg.frisbee.android.event.EventActivity;
import org.gdg.frisbee.android.utils.PrefUtils;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;

import java.util.ArrayList;

import timber.log.Timber;

public class UpcomingEventWidgetProvider extends AppWidgetProvider {

    public static class UpdateService extends Service {
        private ArrayList<Chapter> mChapters;

        @Override
        public void onStart(Intent intent, int startId) {

            ComponentName thisWidget = new ComponentName(this, UpcomingEventWidgetProvider.class);
            AppWidgetManager manager = AppWidgetManager.getInstance(this);

            // Build the widget update for today
            buildUpdate(this, manager, thisWidget);
        }

        @Override
        public IBinder onBind(Intent intent) {
            return null;
        }

        private Chapter findChapter(String chapterId) {
            if (chapterId != null) {
                for (Chapter chapter : mChapters) {
                    if (chapter.getGplusId().equals(chapterId)) {
                        return chapter;
                    }
                }
            }
            return null;
        }

        public void buildUpdate(final Context context, final AppWidgetManager manager, final ComponentName thisWidget) {
            // Pick out month names from resources
            final Resources res = context.getResources();

            final RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_upcoming_event);

            App.getInstance().getModelCache().getAsync("chapter_list_hub", false, new ModelCache.CacheListener() {
                @Override
                public void onGet(Object item) {
                    mChapters = ((Directory) item).getGroups();
                    final Chapter homeGdg = findChapter(PrefUtils.getHomeChapterId(UpdateService.this));

                    if (homeGdg == null) {
                        Timber.d("Got no Home GDG");
                        showErrorChild(views, R.string.loading_data_failed, context);
                    } else {
                        Timber.d("Fetching events");
                        String groupName = homeGdg.getShortName();
                        views.setTextViewText(R.id.groupName, groupName);
                        views.setTextViewText(R.id.groupName2, groupName);
                        GroupDirectory.getChapterEventList(new DateTime(),
                                new DateTime().plusMonths(1),
                                homeGdg.getGplusId(),
                                new Response.Listener<ArrayList<Event>>() {
                                    @Override
                                    public void onResponse(ArrayList<Event> events) {
                                        Timber.d("Got events");
                                        if (events.size() > 0) {
                                            Event firstEvent = events.get(0);
                                            views.setTextViewText(R.id.title, firstEvent.getTitle());
                                            views.setTextViewText(R.id.location, firstEvent.getLocation());
                                            views.setTextViewText(R.id.startDate,
                                                    firstEvent.getStart().toLocalDateTime()
                                                            .toString(DateTimeFormat.patternForStyle("MS", res.getConfiguration().locale)));
                                            showChild(views, 1);

                                            if (firstEvent.getGPlusEventLink() != null) {

                                                String url = firstEvent.getGPlusEventLink();

                                                if (!url.startsWith("http")) {
                                                    url = "https://" + url;
                                                }

                                                Intent i = new Intent(Intent.ACTION_VIEW);
                                                i.setData(Uri.parse(url));
                                                views.setOnClickPendingIntent(R.id.container, PendingIntent.getActivity(context, 0, i, 0));
                                            } else {
                                                Intent i = new Intent(context, EventActivity.class);
                                                i.putExtra(Const.EXTRA_EVENT_ID, firstEvent.getId());
                                                views.setOnClickPendingIntent(R.id.container, PendingIntent.getActivity(context, 0, i, 0));
                                            }
                                        } else {
                                            showErrorChild(views, R.string.no_scheduled_events, context);
                                        }
                                        manager.updateAppWidget(thisWidget, views);
                                    }
                                },
                                new Response.ErrorListener() {
                                    @Override
                                    public void onErrorResponse(VolleyError volleyError) {
                                        Timber.e("Error updating Widget", volleyError);
                                        showErrorChild(views, R.string.loading_data_failed, context);
                                        manager.updateAppWidget(thisWidget, views);
                                    }
                                }

                        ).

                                execute();
                    }
                }

                @Override
                public void onNotFound(String key) {
                    //To change body of implemented methods use File | Settings | File Templates.
                }
            });

        }

        private void showErrorChild(RemoteViews views, int errorStringResource, Context context) {
            views.setTextViewText(R.id.textView_no_events, getString(errorStringResource));
            showChild(views, 0);
            Intent i = new Intent(context, MainActivity.class);
            views.setOnClickPendingIntent(R.id.container, PendingIntent.getActivity(context, 0, i, 0));
        }

        private void showChild(RemoteViews views, int i) {
            if (i == 1) {
                views.setDisplayedChild(R.id.viewFlipper, 1);
            } else {
                views.setDisplayedChild(R.id.viewFlipper, 0);
            }
        }
    }

    @Override
    public void onUpdate(Context context, final AppWidgetManager appWidgetManager, int[] appWidgetIds) {

        context.startService(new Intent(context, UpdateService.class));
    }
}
