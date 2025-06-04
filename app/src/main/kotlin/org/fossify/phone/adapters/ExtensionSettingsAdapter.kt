package org.fossify.phone.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.commons.extensions.setTextColor
import org.fossify.phone.databinding.ItemExtensionSettingBinding
import org.fossify.phone.extensions.core.PhoneExtension

/**
 * Adaptador para mostrar la lista de extensiones en la configuración
 */
class ExtensionSettingsAdapter(
    private val extensions: MutableList<PhoneExtension>,
    private val onExtensionToggled: (PhoneExtension, Boolean) -> Unit,
    private val onExtensionConfigured: (PhoneExtension) -> Unit
) : RecyclerView.Adapter<ExtensionSettingsAdapter.ExtensionViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ExtensionViewHolder {
        val binding = ItemExtensionSettingBinding.inflate(
            LayoutInflater.from(parent.context), 
            parent, 
            false
        )
        return ExtensionViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ExtensionViewHolder, position: Int) {
        holder.bind(extensions[position])
    }

    override fun getItemCount(): Int = extensions.size

    fun addExtension(extension: PhoneExtension) {
        extensions.add(extension)
        notifyItemInserted(extensions.size - 1)
    }

    fun removeExtension(position: Int) {
        if (position in 0 until extensions.size) {
            extensions.removeAt(position)
            notifyItemRemoved(position)
        }
    }

    inner class ExtensionViewHolder(private val binding: ItemExtensionSettingBinding) : 
        RecyclerView.ViewHolder(binding.root) {

        fun bind(extension: PhoneExtension) {
            binding.apply {
                val context = root.context
                
                // Configurar textos
                extensionName.text = extension.name
                extensionDescription.text = extension.description
                extensionVersion.text = "v${extension.version}"
                
                // Configurar colores
                val textColor = context.getProperTextColor()
                extensionName.setTextColor(textColor)
                extensionDescription.setTextColor(textColor)
                extensionVersion.setTextColor(textColor)
                
                // Configurar switch
                extensionSwitch.isChecked = extension.isEnabled()
                extensionSwitch.setOnCheckedChangeListener { _, isChecked ->
                    onExtensionToggled(extension, isChecked)
                }
                
                // Configurar botón de configuración
                extensionConfigButton.setOnClickListener {
                    onExtensionConfigured(extension)
                }
                
                // Click en toda la fila para toggle
                root.setOnClickListener {
                    extensionSwitch.toggle()
                }
            }
        }
    }
}