package eu.darken.myperm.permissions.core

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Parcelable
import androidx.core.content.ContextCompat
import eu.darken.myperm.apps.core.getPermissionInfo2
import eu.darken.myperm.permissions.core.known.AKnownPermissions
import kotlinx.parcelize.Parcelize

interface Permission {
    val id: Id

    fun getLabel(context: Context): String? {
        val pm = context.packageManager

        pm
            .getPermissionInfo2(id, PackageManager.GET_META_DATA)
            ?.takeIf { it.labelRes != 0 }
            ?.loadLabel(pm)
            ?.takeIf { it.isNotEmpty() && it != id.value }
            ?.let { return it.toString() }

        AKnownPermissions.values.singleOrNull { it.id == id }
            ?.labelRes
            ?.let { return context.getString(it) }

        return null
    }

    fun getDescription(context: Context): String? {
        val pm = context.packageManager

        pm
            .getPermissionInfo2(id, PackageManager.GET_META_DATA)
            ?.takeIf { it.labelRes != 0 }
            ?.loadDescription(pm)
            ?.takeIf { it.isNotEmpty() && it != id.value }
            ?.let { return it.toString() }

        AKnownPermissions.values.singleOrNull { it.id == id }
            ?.descriptionRes
            ?.let { return context.getString(it) }

        return null
    }

    fun getIcon(context: Context): Drawable? {
        val pm = context.packageManager

        pm
            .getPermissionInfo2(id, PackageManager.GET_META_DATA)
            ?.takeIf { it.icon != 0 }
            ?.loadIcon(pm)
            ?.let { return it }

        AKnownPermissions.values.singleOrNull { it.id == id }
            ?.iconRes
            ?.let { ContextCompat.getDrawable(context, it) }
            ?.let { return it }

        return null
    }

    @Parcelize
    data class Id(val value: String) : Parcelable

    data class Container(override val id: Id) : Permission
}

fun Permission.Id.toContainer(): Permission.Container = Permission.Container(this)

