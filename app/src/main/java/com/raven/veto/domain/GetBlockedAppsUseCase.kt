package com.raven.veto.domain

import com.raven.veto.data.local.VetoDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class GetBlockedAppsUseCase @Inject constructor(
    private val vetoDao: VetoDao
) {
    operator fun invoke(): Flow<Set<String>> {
        return vetoDao.getAllAppProfilesFlow().map { profiles ->
            profiles.filter { it.isBlocked }.map { it.packageName }.toSet()
        }
    }
}
