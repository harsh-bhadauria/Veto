package com.raven.veto.domain

import com.raven.veto.data.local.PreferencesManager
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetAvailableTimeUseCase @Inject constructor(
    private val preferencesManager: PreferencesManager
) {
    operator fun invoke(): Flow<Long> {
        return preferencesManager.currentBalanceMillisFlow
    }
}
