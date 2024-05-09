/*
 * Minecraft Development for IntelliJ
 *
 * https://mcdev.io/
 *
 * Copyright (C) 2024 minecraft-dev
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published
 * by the Free Software Foundation, version 3.0 only.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.demonwav.mcdev.translations.sorting

import com.demonwav.mcdev.asset.MCDevBundle
import com.demonwav.mcdev.translations.lang.colors.LangSyntaxHighlighter
import com.demonwav.mcdev.TranslationSettings
import com.intellij.codeInsight.template.impl.TemplateEditorUtil
import com.intellij.ide.DataManager
import com.intellij.ide.projectView.ProjectView
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.ex.util.LexerEditorHighlighter
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.ui.DialogPanel
import com.intellij.util.ui.JBUI
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.builder.selected
import com.intellij.ui.layout.ComponentPredicate
import com.intellij.uiDesigner.core.GridConstraints
import java.awt.BorderLayout
import javax.swing.DefaultComboBoxModel
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JPanel
import org.jetbrains.annotations.Nls

class TranslationTemplateConfigurable(private val project: Project) : Configurable {
    private lateinit var panel: JPanel
    private lateinit var innerPanel: JPanel
    private lateinit var cmbScheme: JComboBox<String>
    private lateinit var editorPanel: JPanel
    private lateinit var templateEditor: Editor

    private lateinit var dialogPanel: DialogPanel

    @Nls
    override fun getDisplayName() = "Localization Template"

    override fun getHelpTopic(): String? = null

    override fun createComponent(): JComponent {
        return panel
    }

    private fun getActiveTemplateText() =
        when {
            cmbScheme.selectedIndex == 0 -> TemplateManager.getGlobalTemplateText()
            !project.isDefault -> TemplateManager.getProjectTemplateText(project)
            else -> "You must have selected a project for this!"
        }

    private fun init() {
        dialogPanel = panel {
            val translationSettings = TranslationSettings.getInstance(project)
            row {
                checkBox(MCDevBundle("minecraft.settings.translation.force_json_translation_file"))
                    .bindSelected(translationSettings::isForceJsonTranslationFile)
            }

            lateinit var allowConvertToTranslationTemplate: ComponentPredicate
            row {
                val checkBox = checkBox(MCDevBundle("minecraft.settings.translation.use_custom_convert_template"))
                    .bindSelected(translationSettings::isUseCustomConvertToTranslationTemplate)
                allowConvertToTranslationTemplate = checkBox.selected
            }

            row {
                textField().bindText(translationSettings::convertToTranslationTemplate)
                    .enabledIf(allowConvertToTranslationTemplate)
            }

            onApply {
                for (project in ProjectManager.getInstance().openProjects) {
                    ProjectView.getInstance(project).refresh()
                }
            }
        }
        innerPanel.add(dialogPanel, GridConstraints(), 0)

        if (project.isDefault) {
            cmbScheme.selectedIndex = 0
            cmbScheme.model = DefaultComboBoxModel(arrayOf("Global"))
        } else if (cmbScheme.selectedIndex == 0) {
            cmbScheme.model = DefaultComboBoxModel(arrayOf("Global", "Project"))
        }
        cmbScheme.addActionListener {
            setupEditor()
        }

        setupEditor()
    }

    private fun setupEditor() {
        templateEditor = TemplateEditorUtil.createEditor(false, getActiveTemplateText())
        val editorColorsScheme = EditorColorsManager.getInstance().globalScheme
        val highlighter = LexerEditorHighlighter(
            LangSyntaxHighlighter(TranslationTemplateLexerAdapter()),
            editorColorsScheme,
        )
        (templateEditor as EditorEx).highlighter = highlighter
        templateEditor.settings.isLineNumbersShown = true

        editorPanel.preferredSize = JBUI.size(250, 100)
        editorPanel.minimumSize = editorPanel.preferredSize
        editorPanel.removeAll()
        editorPanel.add(templateEditor.component, BorderLayout.CENTER)
    }

    override fun isModified(): Boolean {
        return templateEditor.document.text != getActiveTemplateText() || dialogPanel.isModified()
    }

    override fun apply() {
        val project = CommonDataKeys.PROJECT.getData(DataManager.getInstance().getDataContext(panel))
        if (cmbScheme.selectedIndex == 0) {
            TemplateManager.writeGlobalTemplate(templateEditor.document.text)
        } else if (project != null) {
            TemplateManager.writeProjectTemplate(project, templateEditor.document.text)
        }

        dialogPanel.apply()
    }

    override fun reset() {
        init()
        dialogPanel.reset()
    }
}
