package com.simple.simpleconvert

import com.intellij.openapi.project.Project
import com.intellij.psi.*
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.PsiShortNamesCache
import com.intellij.psi.util.PsiUtil


class SimpleUtil {
    companion object {
        const val COLLECT: String = "COLLECT"
        const val ARR: String = "ARR"
        const val OBJECT: String = "OBJECT"
        const val BASE: String = "BASE"

        fun getType(psiType: PsiType): String {
            if (isCollection(psiType)) {
                return COLLECT
            }
            if (isArray(psiType)) {
                return ARR
            }
            if (isBaseType(psiType)) {
                return BASE
            }
            return OBJECT
        }

        fun isStaticMethodOrField(psiElement: PsiElement?): Boolean {
            val result = false
            if (isPsiFieldOrMethodOrClass(psiElement)) {
                if (psiElement is PsiMethod) {
                    return isStaticMethod(psiElement)
                } else if (psiElement is PsiField) {
                    return isStaticField(psiElement)
                }
            }
            return result
        }

        /**
         * 静态字段
         *
         * @param psiElement
         * @return
         */
        private fun isStaticField(psiElement: PsiElement?): Boolean {
            var result = false
            if (isPsiFieldOrMethodOrClass(psiElement)) {
                if (psiElement is PsiField) {
                    if (psiElement.hasModifierProperty(PsiModifier.STATIC)) {
                        result = true
                    }
                }
            }
            return result
        }

        /**
         * 静态方法
         *
         * @param psiElement
         * @return
         */
        private fun isStaticMethod(psiElement: PsiElement?): Boolean {
            var result = false
            if (isPsiFieldOrMethodOrClass(psiElement)) {
                if (psiElement is PsiMethod) {
                    if (psiElement.hasModifierProperty(PsiModifier.STATIC)) {
                        result = true
                    }
                }
            }
            return result
        }

        /**
         * 当前是psi 的这个几种类型？ psiElement instanceof JvmMember 兼容性不好 修改为这个 Experimental API interface JvmElement is. This interface can be changed in a future release leading to incompatibilities
         *
         * @param psiElement
         * @return
         */
        private fun isPsiFieldOrMethodOrClass(psiElement: PsiElement?): Boolean {
            return psiElement is PsiField || psiElement is PsiClass || psiElement is PsiMethod || psiElement is PsiJavaFile
        }

        /**
         * 判断类是否来自jdk
         *
         * @param [project] project
         * @param [psiClass] 类
         * @return [Boolean] 是否来自jdk
         */
        private fun isJavaStandardClass(project: Project, psiClass: PsiClass): Boolean {
            // 获取类名
            val className = psiClass.name
                ?: return false // 如果类名为空，则无法判断
            // 获取全局搜索范围
            val scope = GlobalSearchScope.allScope(project)
            // 检查类是否在 Java SDK 中
            val classes = PsiShortNamesCache.getInstance(project).getClassesByName(className, scope)
            for (foundClass in classes) {
                // 判断是否来自 JDK
                val classLocation = foundClass.containingFile.virtualFile.path
                if (classLocation.contains("jdk") || classLocation.contains("java.base")) {
                    return true // 如果类来自 JDK，则认为是 Java 标准库中的类
                }
            }
            return false // 如果类不来自 JDK，则认为不是 Java 标准库中的类
        }

        fun isBaseType(psiType: PsiType): Boolean {
            val canonicalText = psiType.canonicalText
            //基本类型  boolean
            if (PsiTypes.booleanType() == psiType || "java.lang.Boolean" == canonicalText) {
                return true
            }

            //基本类型  String
            if (canonicalText.endsWith("java.lang.String")) {
                return true
            }

            if (PsiTypes.longType() == psiType || "java.lang.Long" == canonicalText) {
                return true
            }

            if (PsiTypes.doubleType() == psiType || "java.lang.Double" == canonicalText) {
                return true
            }

            if (PsiTypes.floatType() == psiType || "java.lang.Float" == canonicalText) {
                return true
            }

            //基本类型  数字
            if (PsiTypes.intType() == psiType || "java.lang.Integer" == canonicalText || PsiTypes.byteType() == psiType || "java.lang.Byte" == canonicalText || PsiTypes.shortType() == psiType || "java.lang.Short" == canonicalText) {
                return true
            }
            //Class xx 特殊class 字段的判断
            //java.lang.Class
            if ("java.lang.Class" == canonicalText) {
                return true
            }


            if ("java.math.BigDecimal" == canonicalText) {
                return true
            }

            if ("java.util.Date" == canonicalText) {
                return true
            }

            if ("java.lang.Object" == canonicalText) {
                return true
            }

            if ("java.time.LocalDateTime" == canonicalText) {
                return true
            }
            val psiClass = PsiUtil.resolveClassInType(psiType)
            if (isJavaStandardClass(psiClass!!.project, psiClass)) {
                return true
            }
            if (psiClass.isEnum) {
                return true
            }
            return false
        }

        private fun isCollection(psiType: PsiType): Boolean {
            val canonicalText = psiType.canonicalText
            //常见的List 和Map
            if (canonicalText.startsWith("java.util.")) {
                if (canonicalText.contains("Map")) {
                    return true
                }
                if (canonicalText.contains("List")) {
                    return true
                }
            }
            return false
        }

        private fun isArray(psiType: PsiType): Boolean {
            val canonicalText = psiType.canonicalText
            //原生的数组
            return canonicalText.contains("[]")
        }

        val baseTextMap = mapOf(
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
        )

        fun isBaseTypeV2(psiType: PsiType): Boolean {
            val canonicalText = psiType.canonicalText
            val psiClass = PsiUtil.resolveClassInType(psiType)
            if (psiClass!!.isEnum) {
                return true
            }
            if (baseTextMap.containsKey(canonicalText)) {
                return true;
            }
            if (isJavaStandardClass(psiClass.project, psiClass)) {
                return true
            }

            println("不再列表中:$canonicalText")
            return false
        }
    }
}