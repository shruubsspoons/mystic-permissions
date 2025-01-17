package eu.darken.myperm.apps.ui.list

import android.content.Context
import androidx.annotation.StringRes
import eu.darken.myperm.R
import eu.darken.myperm.apps.core.Pkg
import eu.darken.myperm.apps.core.features.HasApkData
import eu.darken.myperm.apps.core.features.HasInstallData
import eu.darken.myperm.apps.core.known.AKnownPkg
import java.time.Instant

data class SortOptions(
    val mainSort: Sort = Sort.UPDATED_AT,
    val reversed: Boolean = false
) {
    enum class Sort(
        @StringRes val labelRes: Int
    ) {

        PERMISSIONS_GRANTED(
            labelRes = R.string.apps_sort_permissions_granted_label,
        ) {
            override fun getComparator(c: Context): Comparator<Pkg> = Comparator.comparing<Pkg, Int> { app ->
                (app as? HasApkData)?.requestedPermissions?.count { it.isGranted } ?: 0
            }.reversed()
        },
        PERMISSIONS_REQUESTED(
            labelRes = R.string.apps_sort_permissions_requested_label,
        ) {
            override fun getComparator(c: Context): Comparator<Pkg> = Comparator.comparing<Pkg, Int> { app ->
                (app as? HasApkData)?.requestedPermissions?.size ?: 0
            }.reversed()
        },
        PERMISSIONS_DECLARED(
            labelRes = R.string.apps_sort_permissions_declared_label,
        ) {
            override fun getComparator(c: Context): Comparator<Pkg> = Comparator.comparing<Pkg, Int> { app ->
                (app as? HasApkData)?.declaredPermissions?.size ?: 0
            }.reversed()
        },
        APP_NAME(
            labelRes = R.string.apps_sort_app_name_label,
        ) {
            override fun getComparator(c: Context): Comparator<Pkg> = Comparator.comparing {
                it.getLabel(c) ?: ""
            }
        },
        INSTALLED_AT(
            labelRes = R.string.apps_sort_install_date_label,
        ) {
            override fun getComparator(c: Context): Comparator<Pkg> = Comparator.comparing<Pkg, Instant> {
                (it as? HasInstallData)?.installedAt ?: Instant.MIN
            }.reversed()
        },
        UPDATED_AT(
            labelRes = R.string.apps_sort_update_date_label
        ) {
            override fun getComparator(c: Context): Comparator<Pkg> = Comparator.comparing<Pkg, Instant> {
                (it as? HasInstallData)?.updatedAt ?: Instant.MIN
            }.reversed()
        },
        INSTALL_SOURCE(
            labelRes = R.string.apps_sort_install_source_label
        ) {
            override fun getComparator(c: Context): Comparator<Pkg> = Comparator.comparing<Pkg, Int> { pkg ->
                when {
                    pkg is HasInstallData
                            && pkg.installerInfo.allInstallers.any { it.id == AKnownPkg.GooglePlay.id } -> 3
                    pkg is HasInstallData
                            && pkg.installerInfo.allInstallers.map { it.id }
                        .intersect(AKnownPkg.OEM_STORES.map { it.id }.toSet()).isNotEmpty() -> 2
                    pkg is HasInstallData
                            && pkg.installerInfo.allInstallers.isNotEmpty()
                            && pkg.installerInfo.allInstallers.map { it.id }
                        .intersect(AKnownPkg.APP_STORES.map { it.id }.toSet()).isEmpty() -> 1
                    else -> 0
                }
            }.reversed()
        }
        ;

        abstract fun getComparator(c: Context): Comparator<Pkg>
    }
}