package com.simple.demo5

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.psi.PsiClass

class EditInfo(
    val e: AnActionEvent,
    val psiClass: PsiClass
)