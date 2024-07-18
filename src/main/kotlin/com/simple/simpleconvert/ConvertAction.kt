package com.simple.demo5

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.LangDataKeys
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.psi.codeStyle.CodeStyleManager
import com.simple.simpleconvert.CreateMethod.Companion.createMethodStr
import com.simple.simpleconvert.CustomizeCreateTargetInfo


class ConvertAction : AnAction() {

    override fun actionPerformed(e: AnActionEvent) {
        try {
            val psiFile = e.getData(LangDataKeys.PSI_FILE)
            val editor = CommonDataKeys.EDITOR.getData(e.dataContext)
            if (editor != null) {
                val document = editor.document
                var lineNumberCurrent = document.getLineNumber(editor.caretModel.offset)
                val startOffset: Int = document.getLineStartOffset(lineNumberCurrent)
                //生成转换代码
                val codeStr = createMethodStr(
                    //获取出参
                    CustomizeCreateTargetInfo.resolveReturnType(e),
                    //获取入参
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
        } catch (e: Exception) {
            JBPopupFactory.getInstance()
                .createMessage("出现错误:${e.message}")
                .showInFocusCenter();
        }
    }
}