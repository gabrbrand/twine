/*
 * Copyright 2023 Sasikanth Miriyampalli
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
package dev.sasikanth.rss.reader

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequest
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkerParameters
import co.touchlab.crashkios.bugsnag.BugsnagKotlin
import com.bugsnag.android.Bugsnag
import dev.sasikanth.rss.reader.data.repository.RssRepository
import dev.sasikanth.rss.reader.data.repository.SettingsRepository
import dev.sasikanth.rss.reader.refresh.LastUpdatedAt
import dev.sasikanth.rss.reader.utils.ObservableDate
import java.time.Duration
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

class FeedsRefreshWorker(
  context: Context,
  workerParameters: WorkerParameters,
  private val rssRepository: RssRepository,
  private val lastUpdatedAt: LastUpdatedAt,
  private val observableDate: ObservableDate,
  private val settingsRepository: SettingsRepository,
) : CoroutineWorker(context, workerParameters) {

  companion object {

    const val TAG = "REFRESH_FEEDS"

    fun periodicRequest(): PeriodicWorkRequest {
      val constraints =
        Constraints.Builder()
          .setRequiredNetworkType(NetworkType.CONNECTED)
          .setRequiresBatteryNotLow(true)
          .build()

      return PeriodicWorkRequestBuilder<FeedsRefreshWorker>(repeatInterval = Duration.ofHours(1))
        .setConstraints(constraints)
        .build()
    }
  }

  override suspend fun doWork(): Result {
    if (settingsRepository.enableAutoSync.first().not()) return Result.failure()

    return if (lastUpdatedAt.hasExpired()) {
      try {
        rssRepository.updateFeeds()
        lastUpdatedAt.refresh()
        observableDate.refresh()
        Result.success()
      } catch (e: CancellationException) {
        Result.failure()
      } catch (e: Exception) {
        Bugsnag.leaveBreadcrumb("Background Worker")
        BugsnagKotlin.sendFatalException(e)
        Result.failure()
      }
    } else {
      Result.failure()
    }
  }
}
