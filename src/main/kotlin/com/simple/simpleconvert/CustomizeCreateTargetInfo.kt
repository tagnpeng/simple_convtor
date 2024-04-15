package com.simple.demo5

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.LangDataKeys
import com.intellij.openapi.editor.Editor
import com.intellij.psi.*
import com.intellij.psi.impl.source.PsiClassReferenceType
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.PsiUtil
import com.simple.simpleconvert.SimpleUtil
import org.apache.commons.lang3.CharUtils
import java.util.*
import java.util.regex.Pattern
import java.util.stream.Collectors
import java.util.stream.Stream

open class CustomizeCreateTargetInfo {
    companion object {
        fun getGenerateInfo(event: AnActionEvent): EditInfo {
            val editor: Editor? = CommonDataKeys.EDITOR.getData(event.dataContext)
            val psiFile: PsiFile? = event.getData(LangDataKeys.PSI_FILE)

            //定位当前类
            val element = psiFile!!.findElementAt(editor!!.caretModel.offset)
            val pisClass = PsiTreeUtil.getParentOfType(element, PsiClass::class.java)!!

            //获取解析后的出参和入参
            return EditInfo(event, pisClass)
        }


        fun resolveReturnType(event: AnActionEvent): TargetClass {
            val editor: Editor? = CommonDataKeys.EDITOR.getData(event.dataContext)
            val psiFile: PsiFile? = event.getData(LangDataKeys.PSI_FILE)
            //定位当前方法
            val method: PsiMethod? = PsiTreeUtil.getParentOfType(
                psiFile!!.findElementAt(editor!!.caretModel.offset),
                PsiMethod::class.java
            )

            val returnPsiType = method!!.returnType
            if (!method.hasParameters()) {
                throw Exception("必须需要一个出参");
            }
            var resultPsiClass = PsiUtil.resolveClassInType(returnPsiType)
            val returnType: String = SimpleUtil.getType(returnPsiType!!)
            if ((returnType == SimpleUtil.ARR || returnType == SimpleUtil.COLLECT) && returnPsiType is PsiClassReferenceType) {
                val optionalPsiType = Arrays.stream(returnPsiType.parameters)
                    .filter { obj: PsiType? -> Objects.nonNull(obj) }.findFirst()
                if (optionalPsiType.isPresent) {
                    if (SimpleUtil.getType(optionalPsiType.get()) != SimpleUtil.BASE) {
                        resultPsiClass = PsiUtil.resolveClassInType(optionalPsiType.get())
                    } else {
                        throw Exception("返回值不支持基础类型");
                    }
                }
            }
            val targetClass = getTargetClass(resultPsiClass!!, TargetClass(), "set")
            targetClass.type = returnType;
            return targetClass
        }

        fun resolveEnterType(event: AnActionEvent): TargetClass {
            val editor: Editor? = CommonDataKeys.EDITOR.getData(event.dataContext)
            val psiFile: PsiFile? = event.getData(LangDataKeys.PSI_FILE)
            //定位当前方法
            val method: PsiMethod? = PsiTreeUtil.getParentOfType(
                psiFile!!.findElementAt(editor!!.caretModel.offset),
                PsiMethod::class.java
            )

            val enterPsiType = method!!.parameterList.parameters
            if (enterPsiType.isEmpty()) {
                throw Exception("必须需要一个入参");
            }
            val paramType = SimpleUtil.getType(enterPsiType[0].type)
            var paramPsiClass = PsiUtil.resolveClassInType(enterPsiType[0].type)
            if ((paramType == SimpleUtil.ARR || paramType == SimpleUtil.COLLECT) && enterPsiType[0]
                    .type is PsiClassReferenceType
            ) {
                val psiClassReferenceType = enterPsiType[0].type as PsiClassReferenceType
                val optionalPsiType = Arrays.stream(psiClassReferenceType.parameters)
                    .filter { obj: PsiType? -> Objects.nonNull(obj) }.findFirst()
                if (optionalPsiType.isPresent) {
                    if (SimpleUtil.getType(optionalPsiType.get()) != SimpleUtil.BASE) {
                        paramPsiClass = PsiUtil.resolveClassInType(optionalPsiType.get())
                    } else {
                        throw Exception("不支持基础类型");
                    }
                }
            }
            val targetClass = getTargetClass(paramPsiClass!!, TargetClass(), "get")
            targetClass.type = paramType
            return targetClass
        }

        /**
         * 获取目标class信息
         * @param [psiClass]
         * @param [aClass]
         * @param [typeStr] 是提取get还是set
         * @return [Class<*>]
         */
        @Throws(java.lang.Exception::class)
        fun getTargetClass(psiClass: PsiClass, aClass: TargetClass, typeStr: String): TargetClass {
            val psiClassFields = Stream.of(*psiClass.fields).collect(Collectors.toList())
            //拿到当前类名称
            aClass.className = psiClass.name
            aClass.paramName =
                aClass.className?.substring(0, 1)!!.lowercase(Locale.getDefault()) + aClass.className!!.substring(1)

            //判断当前类是否还有超类
            val supers = psiClass.supers
            if (supers.isNotEmpty()) {
                for (aSuper in supers) {
                    //todo 可以提取特征将不需要管的超类过滤
                    if (aSuper.name == "DTO"
                        || aSuper.name == "Serializable"
                        || aSuper.name == "Object"
                        || aSuper.name == "BigDecimal"
                        || aSuper.name == "Enum"
                    ) {
                        continue
                    }
                    psiClassFields.addAll(Stream.of(*aSuper.fields).collect(Collectors.toList()))
                }
            }

            //获取当前类所有属性的set或者get方法
            val methods: Map<String, String> = getMethods(psiClass, typeStr)
            val fields: MutableList<TargetField> = ArrayList<TargetField>()
            for (psiField in psiClassFields) {
                if (SimpleUtil.isStaticMethodOrField(psiField) || psiField.name == "serialVersionUID") {
                    continue
                }
                val field = TargetField()
                field.fieldName = psiField.name

                val psiType = psiField.type
                val fieldPsiClass = PsiUtil.resolveClassInType(psiType)
                val type = SimpleUtil.getType(psiType)
                if (!methods.containsKey(psiField.name)) {
                    throw Exception(psiClass.name + "." + psiField.name + typeStr + "方法命名不支持")
                }
                field.method = methods[psiField.name]
                field.type = type
                field.psiType = psiType
                field.psiClass = fieldPsiClass
                //当前属性可能还存在对象或者数组等不是基础类型的,则需要继续深层获取信息
                if (type == SimpleUtil.OBJECT || type == SimpleUtil.ARR) {
                    val sonFieldPsiClass = TargetClass()
                    sonFieldPsiClass.type = type;
                    getTargetClass(fieldPsiClass!!, sonFieldPsiClass, typeStr)
                    field.targetClass = sonFieldPsiClass
                }
                //属性为数组需要判断泛型
                if (type == SimpleUtil.COLLECT && psiType is PsiClassReferenceType) {
                    val sonFieldPsiClass = TargetClass()
                    sonFieldPsiClass.type = type;
                    val optionalPsiType = Arrays.stream(psiType.parameters)
                        .filter { obj: PsiType? -> Objects.nonNull(obj) }.findFirst()
                    if (optionalPsiType.isPresent) {
                        if (SimpleUtil.getType(optionalPsiType.get()) != SimpleUtil.BASE) {
                            getTargetClass(
                                PsiUtil.resolveClassInType(optionalPsiType.get())!!,
                                sonFieldPsiClass,
                                typeStr
                            )
                            field.targetClass = sonFieldPsiClass
                        }
                    }
                }
                fields.add(field)
            }
            aClass.targetField = fields
            return aClass
        }

        /**
         * 提取所有方法
         *
         * @param [psiClass] 当前类
         * @param [typeStr] 提取set还是get方法
         * @return [Map<String, String>]
         */
        private fun getMethods(psiClass: PsiClass, typeStr: String): Map<String, String> {
            val fieldMethod: MutableMap<String, String> = HashMap()
            //拿到所有超类和当前类之后遍历
            val supers = Stream.of(*psiClass.supers).collect(Collectors.toList())
            supers!!.add(psiClass)
            if (supers.size > 0) {
                for (aSuper in supers) {
                    //todo 可以提取特征将不需要管的超类过滤
                    if ((aSuper.name == "DTO") || (aSuper.name == "Serializable") || (aSuper.name == "Object")) {
                        continue
                    }
                    //遍历所有字段
                    for (field in aSuper.fields) {
                        //遍历所有方法匹配字段,一定需要符合标准的
                        for (psiMethod in aSuper.methods) {
                            //手动拼接get或者set方法
                            val cs: CharArray = field.name.toCharArray()
                            if (!CharUtils.isAsciiAlphaUpper(cs[0])) {
                                cs[0] = cs[0] - 32
                            }
                            //判断拼接后的放在是否在类中存在,或者类中的方法本身就存在
                            if ((typeStr + String(cs)) == psiMethod.name || Pattern.matches(
                                    typeStr + String(cs) + "\\w",
                                    psiMethod.name
                                )
                            ) {
                                fieldMethod[field.name] = psiMethod.name
                            }
                        }
                    }
                    //如果使用了lombok注解
                    if (isUsedLombokData(aSuper)) {
                        val p = Pattern.compile("static.*?final|final.*?static")
                        val fields = aSuper.fields
                        for (psiField in fields) {
                            val context = psiField.nameIdentifier.context ?: continue
                            val fieldVal = context.text
                            // serialVersionUID 判断
                            if (fieldVal.contains("serialVersionUID")) {
                                continue
                            }
                            // static final 常量判断过滤
                            val matcher = p.matcher(fieldVal)
                            if (matcher.find()) {
                                continue
                            }
                            val name = psiField.nameIdentifier.text
                            //直接拼接
                            fieldMethod[name] =
                                typeStr + name.substring(0, 1).uppercase(Locale.getDefault()) + name.substring(1)
                        }
                    }
                }
            }
            return fieldMethod
        }

        private fun isUsedLombokData(psiClass: PsiClass): Boolean {
            return null != psiClass.getAnnotation("lombok.Data")
        }
    }
}