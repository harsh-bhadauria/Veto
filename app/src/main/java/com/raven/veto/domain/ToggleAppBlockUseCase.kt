package com.raven.veto.domain

import com.raven.veto.data.local.AppProfileEntity
import com.raven.veto.data.local.VetoDao
import javax.inject.Inject

class ToggleAppBlockUseCase @Inject constructor(
    private val vetoDao: VetoDao
) {
    suspend operator fun invoke(packageName: String) {
        val existingProfile = vetoDao.getAppProfile(packageName)
        if (existingProfile != null) {
            vetoDao.upsertAppProfile(existingProfile.copy(isBlocked = !existingProfile.isBlocked))
        } else {
            vetoDao.upsertAppProfile(AppProfileEntity(packageName = packageName, isBlocked = true))
        }
    }
}
