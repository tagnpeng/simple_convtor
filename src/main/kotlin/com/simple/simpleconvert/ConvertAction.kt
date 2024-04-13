package com.simple.demo5

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.LangDataKeys
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.codeStyle.CodeStyleManager
import com.simple.demo5.CreateMethod.Companion.createMethodStr


class ConvertAction : AnAction() {

    override fun actionPerformed(e: AnActionEvent) {
        val psiFile = e.getData(LangDataKeys.PSI_FILE)
        val editor = CommonDataKeys.EDITOR.getData(e.dataContext)
        if (editor != null) {
            val document = editor.document
            var lineNumberCurrent = document.getLineNumber(editor.caretModel.offset)
            val startOffset: Int = document.getLineStartOffset(lineNumberCurrent)
            val codeStr = createMethodStr(
                CustomizeCreateTargetInfo.resolveReturnType(e),
                CustomizeCreateTargetInfo.resolveEnterType(e),
                CustomizeCreateTargetInfo.getGenerateInfo(e)
            )
            WriteCommandAction.runWriteCommandAction(e.project) {
                document.insertString(
                    document.getLineStartOffset(lineNumberCurrent++),
                    codeStr
                )
                e.project?.let {
                    CodeStyleManager.getInstance(it)
                        .reformatText(psiFile!!, startOffset, document.getLineEndOffset(lineNumberCurrent))
                }
            }
        }
    }
}