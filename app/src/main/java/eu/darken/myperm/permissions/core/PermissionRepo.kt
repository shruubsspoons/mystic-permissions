package eu.darken.myperm.permissions.core

import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.PermissionInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.myperm.apps.core.AppRepo
import eu.darken.myperm.apps.core.features.HasApkData
import eu.darken.myperm.apps.core.getPermissionInfo2
import eu.darken.myperm.common.coroutine.AppScope
import eu.darken.myperm.common.debug.logging.Logging.Priority.ERROR
import eu.darken.myperm.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.myperm.common.debug.logging.asLog
import eu.darken.myperm.common.debug.logging.log
import eu.darken.myperm.common.debug.logging.logTag
import eu.darken.myperm.common.flow.shareLatest
import eu.darken.myperm.permissions.core.types.BasePermission
import eu.darken.myperm.permissions.core.types.DeclaredPermission
import eu.darken.myperm.permissions.core.types.UnknownPermission
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PermissionRepo @Inject constructor(
    @ApplicationContext private val context: Context,
    @AppScope private val appScope: CoroutineScope,
    private val packageManager: PackageManager,
    private val appRepo: AppRepo,
) {

    private val refreshTrigger = MutableStateFlow(UUID.randomUUID())

    fun refresh() {
        log(TAG) { "refresh() " }
        refreshTrigger.value = UUID.randomUUID()
    }

    val permissions: Flow<Collection<BasePermission>> = combine(
        appRepo.apps,
        refreshTrigger
    ) { apps, _ ->

        val fromAosp = (packageManager.getAllPermissionGroups(0) + listOf(null))
            .mapNotNull { permissionGroup ->
                val name = permissionGroup?.name
                log(TAG, VERBOSE) { "Querying permission group $name" }
                try {
                    packageManager.queryPermissionsByGroup(name, 0)
                } catch (e: PackageManager.NameNotFoundException) {
                    log(TAG) { "Failed to retrieve permission group $permissionGroup: $e" }
                    null
                }
            }
            .flatten()

        val appWithPermissions = apps.filterIsInstance<HasApkData>()

        val mappedPermissions = mutableSetOf<BasePermission>()

        // All we know from the system
        fromAosp
            .map { it.toDeclaredPermission(appWithPermissions) }
            .distinctBy { it.id }
            .let {
                log(TAG) { "${it.size} permissions from AOSP" }
                mappedPermissions.addAll(it)
            }


        // All that apps have declared themselves
        appWithPermissions
            .map { it.declaredPermissions }
            .flatten()
            .filter { newPerm -> mappedPermissions.none { it.id == newPerm.id } }
            .map { it.toDeclaredPermission(appWithPermissions) }
            .let {
                log(TAG) { "${it.size} permissions declared by apps" }
                mappedPermissions.addAll(it)
            }

        // All that are specified by apps via `uses-permission`
        // It's possible that some of these are unused as no other app declares them.
        appWithPermissions
            .asSequence()
            .map { usesPerms -> usesPerms.requestedPermissions.map { it.id } }
            .flatten()
            .distinct()
            .filter { newPerm -> mappedPermissions.none { it.id == newPerm } }
            .map { id ->
                val info = packageManager.getPermissionInfo2(id, PackageManager.GET_META_DATA)
                when {
                    info != null -> info.toDeclaredPermission(appWithPermissions)
                    else -> id.toUnusedPermission(appWithPermissions)
                }
            }
            .distinctBy { it.id }
            .toList()
            .let {
                log(TAG) { "${it.size} unknown permissions requested by apps" }
                mappedPermissions.addAll(it)
            }

        mappedPermissions.also { log(TAG) { "${it.size} total permissions discovered." } }
    }
        .catch {
            log(TAG, ERROR) { "Failed to generate permission data: ${it.asLog()}" }
            throw it
        }
        .shareLatest(scope = appScope, started = SharingStarted.Lazily)

    private fun PermissionInfo.toDeclaredPermission(apps: Collection<HasApkData>): DeclaredPermission =
        DeclaredPermission(
            permissionInfo = this,
            requestingPkgs = apps.filter { it.requestsPermission(id) },
            declaringPkgs = apps.filter { it.declaresPermission(id) }
        )

    private fun Permission.Id.toUnusedPermission(apps: Collection<HasApkData>): UnknownPermission = UnknownPermission(
        id = this,
        requestingPkgs = apps.filter { it.requestsPermission(this) }
    )

    companion object {
        private val TAG = logTag("Permissions", "Repo")
    }
}