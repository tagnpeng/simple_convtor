package com.simple.simpleconvert

import com.intellij.openapi.project.Project
import com.intellij.psi.*
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.PsiShortNamesCache
import com.intellij.psi.util.PsiUtil

/**
 * 工具类，提供与PsiType相关的辅助方法
 */
class SimpleUtil {
    companion object {
        const val COLLECT: String = "COLLECT"
        const val ARR: String = "ARR"
        const val OBJECT: String = "OBJECT"
        const val BASE: String = "BASE"

        /**
         * 根据PsiType获取类型字符串
         */
        fun getType(psiType: PsiType): String {
            return when {
                isBaseType(psiType) -> BASE
                isArray(psiType) -> ARR
                isCollection(psiType) -> COLLECT
                else -> OBJECT
            }
        }

        /**
         * 判断Psi元素是否为静态方法或字段
         */
        fun isStaticMethodOrField(psiElement: PsiElement?): Boolean {
            return when {
                isPsiFieldOrMethodOrClass(psiElement) && psiElement is PsiMethod -> isStaticMethod(psiElement)
                isPsiFieldOrMethodOrClass(psiElement) && psiElement is PsiField -> isStaticField(psiElement)
                else -> false
            }
        }

        /**
         * 判断Psi元素是否为静态字段
         */
        private fun isStaticField(psiElement: PsiElement?): Boolean {
            return isPsiFieldOrMethodOrClass(psiElement) && (psiElement as? PsiField)?.hasModifierProperty(PsiModifier.STATIC) ?: false
        }

        /**
         * 判断Psi元素是否为静态方法
         */
        private fun isStaticMethod(psiElement: PsiElement?): Boolean {
            return isPsiFieldOrMethodOrClass(psiElement) && (psiElement as? PsiMethod)?.hasModifierProperty(PsiModifier.STATIC) ?: false
        }

        /**
         * 判断Psi元素是否为PsiField或PsiMethod或PsiClass或PsiJavaFile
         */
        private fun isPsiFieldOrMethodOrClass(psiElement: PsiElement?): Boolean {
            return psiElement is PsiField || psiElement is PsiClass || psiElement is PsiMethod || psiElement is PsiJavaFile
        }

        /**
         * 判断PsiType是否为基本数据类型
         */
        fun isBaseType(psiType: PsiType): Boolean {
            val canonicalText = psiType.canonicalText
            val resolveClassInType = PsiUtil.resolveClassInType(psiType)
            return baseTextMap.containsKey(canonicalText) || (resolveClassInType
                ?.let { isJavaStandardClass(it.project, it) } ?: false) || resolveClassInType!!.isEnum
        }

        /**
         * 判断PsiType是否为集合类型
         */
        private fun isCollection(psiType: PsiType): Boolean {
            val canonicalText = psiType.canonicalText
            return canonicalText.startsWith("java.util.") && (canonicalText.contains("Map") || canonicalText.contains("List"))
        }

        /**
         * 判断PsiType是否为数组类型
         */
        private fun isArray(psiType: PsiType): Boolean {
            return psiType.canonicalText.contains("[]")
        }

        /**
         * 判断PsiClass是否为Java标准库中的类
         */
        private fun isJavaStandardClass(project: Project, psiClass: PsiClass): Boolean {
            val className = psiClass.name ?: return false
            val scope = GlobalSearchScope.allScope(project)
            val classes = PsiShortNamesCache.getInstance(project).getClassesByName(className, scope)
            for (foundClass in classes) {
                val classLocation = foundClass.containingFile.virtualFile.path
                if (classLocation.contains("jdk") || classLocation.contains("java.base")) {
                    return true
                }
            }
            return false
        }

        /**
         * 预定义的基本数据类型映射
         */
        private val baseTextMap = mapOf(
            "java.lang.Boolean" to 1,
            "java.lang.String" to 1,
            "java.lang.Long" to 1,
            "java.lang.Double" to 1,
            "java.lang.Float" to 1,
            "java.lang.Integer" to 1,
            "java.lang.Byte" to 1,
            "java.lang.Short" to 1,
            "java.lang.Class" to 1,
            "java.math.BigDecimal" to 1,
            "java.util.Date" to 1,
            "java.lang.Object" to 1,
            "java.time.LocalDateTime" to 1,
            "int" to 1,
            "long" to 1,
            "double" to 1,
            "float" to 1,
            "boolean" to 1,
            "byte" to 1,
            "short" to 1,
            "char" to 1,
            "void" to 1,
            "java.lang.String[]" to 1,
            "java.lang.Boolean[]" to 1,
            "java.lang.Long[]" to 1,
            "java.lang.Double[]" to 1,
            "java.lang.Float[]" to 1,
            "java.lang.Integer[]" to 1,
            "java.lang.Byte[]" to 1,
            "java.lang.Short[]" to 1,
            "java.lang.Class[]" to 1,
            "java.math.BigDecimal[]" to 1,
            "java.util.Date[]" to 1,
            "java.lang.Object[]" to 1,
            "java.time.LocalDateTime[]" to 1,
            "int[]" to 1,
            "long[]" to 1,
            "double[]" to 1,
            "float[]" to 1,
            "boolean[]" to 1,
            "byte[]" to 1
        )
    }
}
