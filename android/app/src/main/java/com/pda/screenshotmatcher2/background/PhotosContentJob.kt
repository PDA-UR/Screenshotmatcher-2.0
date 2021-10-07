package com.pda.screenshotmatcher2.background

import android.app.job.JobInfo
import android.app.job.JobInfo.TriggerContentUri
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.annotation.RequiresApi
import java.util.*


/**
 * Example stub job to monitor when there is a change to photos in the media provider.
 */
@RequiresApi(Build.VERSION_CODES.N)
class PhotosContentJob : JobService() {
    companion object {
        // The root URI of the media provider, to monitor for generic changes to its content.
        val MEDIA_URI =
            Uri.parse("content://" + MediaStore.AUTHORITY + "/")

        // Path segments for image-specific URIs in the provider.
        val EXTERNAL_PATH_SEGMENTS =
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI.pathSegments

        // The columns we want to retrieve about a particular image.
        val PROJECTION = arrayOf(
            MediaStore.Images.ImageColumns._ID, MediaStore.Images.ImageColumns.DATA
        )
        const val PROJECTION_ID = 0
        const val PROJECTION_DATA = 1

        // This is the external storage directory where cameras place pictures.
        val DCIM_DIR = Environment.getExternalStoragePublicDirectory(
            Environment.DIRECTORY_DCIM
        ).path

        // A pre-built JobInfo we use for scheduling our job.
        var JOB_INFO: JobInfo? = null

        // Schedule this job, replace any existing one.
        fun scheduleJob(context: Context) {
            val js = context.getSystemService(JobScheduler::class.java)
            js.schedule(JOB_INFO!!)
            Log.i("PhotosContentJob", "JOB SCHEDULED!")
        }

        // Check whether this job is currently scheduled.
        fun isScheduled(context: Context): Boolean {
            val js = context.getSystemService(JobScheduler::class.java)
            val jobs = js.allPendingJobs ?: return false
            for (i in jobs.indices) {
                if (jobs[i].id == JobIds.PHOTOS_CONTENT_JOB) {
                    return true
                }
            }
            return false
        }


        // Cancel this job, if currently scheduled.
        fun cancelJob(context: Context) {
            val js = context.getSystemService(JobScheduler::class.java)
            js.cancel(JobIds.PHOTOS_CONTENT_JOB)
        }

        init {
            val builder = JobInfo.Builder(
                JobIds.PHOTOS_CONTENT_JOB,
                ComponentName("com.pda.screenshotmatcher2", PhotosContentJob::class.java.name)
            )
            // Look for specific changes to images in the provider.
            builder.addTriggerContentUri(
                TriggerContentUri(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    TriggerContentUri.FLAG_NOTIFY_FOR_DESCENDANTS
                )
            )
            // Also look for general reports of changes in the overall provider.
            builder.addTriggerContentUri(TriggerContentUri(MEDIA_URI, 0))
            JOB_INFO = builder.build()
        }
    }

    // Fake job work.  A real implementation would do some work on a separate thread.
    val mHandler = Handler()
    val mWorker = Runnable {
        scheduleJob(this@PhotosContentJob)
        jobFinished(mRunningParams, true)
    }

    var mRunningParams: JobParameters? = null
    override fun onStartJob(params: JobParameters): Boolean {
        Log.i("PhotosContentJob", "JOB STARTED!")
        mRunningParams = params

        // Instead of real work, we are going to build a string to show to the user.
        val sb = StringBuilder()

        // Did we trigger due to a content change?
        if (params.triggeredContentAuthorities != null) {
            Log.d("PCJ", "content changed")
            var rescanNeeded = false
            if (params.triggeredContentUris != null) {
                Log.d("PCJ", "new uris")
                // If we have details about which URIs changed, then iterate through them
                // and collect either the ids that were impacted or note that a generic
                // change has happened.
                val ids = ArrayList<String>()
                for (uri in params.triggeredContentUris!!) {
                    val path = uri.pathSegments
                    if (path != null && path.size == EXTERNAL_PATH_SEGMENTS.size + 1) {
                        // This is a specific file.
                        ids.add(path[path.size - 1])
                    } else {
                        // Oops, there is some general change!
                        rescanNeeded = true
                    }
                }
                if (ids.size > 0) {
                    // If we found some ids that changed, we want to determine what they are.
                    // First, we do a query with content provider to ask about all of them.
                    val selection = StringBuilder()
                    for (i in ids.indices) {
                        if (selection.length > 0) {
                            selection.append(" OR ")
                        }
                        selection.append(MediaStore.Images.ImageColumns._ID)
                        selection.append("='")
                        selection.append(ids[i])
                        selection.append("'")
                    }

                    // Now we iterate through the query, looking at the filenames of
                    // the items to determine if they are ones we are interested in.
                    var cursor: Cursor? = null
                    var haveFiles = false
                    try {
                        cursor = contentResolver.query(
                            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                            PROJECTION, selection.toString(), null, null
                        )
                        while (cursor!!.moveToNext()) {
                            // We only care about files in the DCIM directory.
                            val dir =
                                cursor.getString(PROJECTION_DATA)
                            if (dir.startsWith(DCIM_DIR)) {
                                if (!haveFiles) {
                                    haveFiles = true
                                    sb.append("New photos:\n")
                                }
                                sb.append(cursor.getInt(PROJECTION_ID))
                                sb.append(": ")
                                sb.append(dir)
                                sb.append("\n")
                            }
                        }
                    } catch (e: SecurityException) {
                        sb.append("Error: no access to media!")
                    } finally {
                        cursor?.close()
                    }
                }
            } else {
                // We don't have any details about URIs (because too many changed at once),
                // so just note that we need to do a full rescan.
                rescanNeeded = true
            }
            if (rescanNeeded) {
                sb.append("Photos rescan needed!")
            }
        } else {
            sb.append("(No photos content)")
        }
        Toast.makeText(this, sb.toString(), Toast.LENGTH_LONG).show()
        Log.d("PCJ", sb.toString())
        // We will emulate taking some time to do this work, so we can see batching happen.
        mHandler.post(mWorker)
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        mHandler.removeCallbacks(mWorker)
        return false
    }
}
object JobIds {
    const val PHOTOS_CONTENT_JOB = 1
}