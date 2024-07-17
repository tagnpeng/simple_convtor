package com.simple.simpleconvert

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.PsiElementFactory
import com.simple.demo5.EditInfo
import com.simple.demo5.TargetClass
import com.simple.demo5.TargetField
import org.apache.commons.text.similarity.LevenshteinDistance
import java.util.stream.Collectors

// Todo: 编写判断变量重复工具
class CreateMethod {
    companion object {
        private val levenshtein = LevenshteinDistance()
        private const val threshold = 4

        /**
         * 在map中搜索相似key并且返回值
         *
         * @param map 包含目标字段的map
         * @param result 搜索目标字段
         * @return 相似字段或null
         */
        private fun searchSimilar(map: Map<String, TargetField>, result: TargetField): TargetField? {
            var closestMatch: TargetField? = null
            var currDistance = 10

            map.forEach { (key, value) ->
                val distance = levenshtein.apply(key, result.fieldName)
                // 参数名相同直接返回
                if (distance <= 0) return value
                if (closestMatch == null && distance <= threshold) {
                    closestMatch = value
                    currDistance = distance
                } else if (distance < currDistance || (distance == threshold && value.psiType?.canonicalText == result.psiType?.canonicalText)) {
                    closestMatch = value
                    currDistance = distance
                }
            }

            return closestMatch
        }

        /**
         * 创建转换方法字符串
         *
         * @param resultTarget 返回类
         * @param enterTarget 输入类
         * @param editInfo 编辑信息
         * @return 转换方法字符串
         */
        fun createMethodStr(resultTarget: TargetClass, enterTarget: TargetClass, editInfo: EditInfo): String {
            if (resultTarget.type != enterTarget.type) {
                throw Exception("${resultTarget.paramName}与${enterTarget.paramName}类型不一致(${resultTarget.type}, ${enterTarget.type})")
            }
            return createMethodHeader(resultTarget, enterTarget, editInfo)
        }

        /**
         * 创建方法头信息
         *
         * @param resultTarget 返回类
         * @param enterTarget 输入类
         * @param editInfo 编辑信息
         * @return 方法头字符串
         */
        private fun createMethodHeader(
            resultTarget: TargetClass,
            enterTarget: TargetClass,
            editInfo: EditInfo
        ): String {
            val str = StringBuilder()
            when (enterTarget.type) {
                SimpleUtil.OBJECT, SimpleUtil.ARR -> str.append("if(${enterTarget.paramName}==null){return null;}")
                SimpleUtil.COLLECT -> str.append("if(${enterTarget.paramName}==null){ return new ArrayList<>();}")
            }
            str.append(createMethodMain(resultTarget, enterTarget, editInfo))
            return str.toString()
        }

        /**
         * 创建方法主体
         *
         * @param resultTarget 返回类
         * @param enterTarget 输入类
         * @param editInfo 编辑信息
         * @return 方法主体字符串
         */
        private fun createMethodMain(resultTarget: TargetClass, enterTarget: TargetClass, editInfo: EditInfo): String {
            val str = StringBuilder()
            when (resultTarget.type) {
                SimpleUtil.ARR -> {
                    str.append("${resultTarget.className}[] list = new ${resultTarget.className}[${enterTarget.paramName}.length];")
                    str.append("for(int i = 0; i < ${enterTarget.paramName}.length; i++){")
                    str.append("${enterTarget.className} iterationItem = ${enterTarget.paramName}[i];")
                    str.append(createAssignment("iterationItem", resultTarget, enterTarget, editInfo))
                    str.append("list[i] = ${resultTarget.paramName};")
                    str.append("}")
                    str.append("return list;")
                }

                SimpleUtil.COLLECT -> {
                    str.append("List<${resultTarget.className}> list = new ArrayList<>();")
                    str.append("for(${enterTarget.className} iterationItem : ${enterTarget.paramName}){")
                    str.append(createAssignment("iterationItem", resultTarget, enterTarget, editInfo))
                    str.append("list.add(${resultTarget.paramName});")
                    str.append("}")
                    str.append("return list;")
                }

                else -> {
                    str.append(createAssignment(enterTarget.paramName!!, resultTarget, enterTarget, editInfo))
                    str.append("return ${resultTarget.paramName};")
                }
            }
            return str.toString()
        }

        /**
         * 创建属性赋值字符串
         *
         * @param name 当前迭代项名称
         * @param resultTarget 返回类
         * @param enterTarget 输入类
         * @param editInfo 编辑信息
         * @return 属性赋值字符串
         */
        private fun createAssignment(
            name: String,
            resultTarget: TargetClass,
            enterTarget: TargetClass,
            editInfo: EditInfo
        ): String {
            val str = StringBuilder()
            val paramFieldMap: Map<String, TargetField> = enterTarget.targetField!!.stream()
                .collect(Collectors.toMap({ it.fieldName }, { it }))

            str.append("${resultTarget.className} ${resultTarget.paramName} = new ${resultTarget.className}();")
            resultTarget.targetField!!.forEach { item ->
                val paramField: TargetField? = searchSimilar(paramFieldMap, item)
                str.append("${resultTarget.paramName}.${item.method}(")
                if (paramField != null) {
                    if (item.type == SimpleUtil.ARR || item.type == SimpleUtil.COLLECT || item.type == SimpleUtil.OBJECT) {
                        val methodName = createNewMethod(item.targetClass!!, paramField.targetClass!!, editInfo)
                        str.append("$methodName(${name}.${paramField.method}())")
                    } else {
                        str.append("$name.${paramField.method}()")
                    }
                } else {
                    str.append("null")
                }
                str.append(");")
            }
            return str.toString()
        }

        /**
         * 生成新方法
         *
         * @param resultTarget 返回类
         * @param enterTarget 输入类
         * @param editInfo 编辑信息
         * @return 新方法名称
         */
        private fun createNewMethod(resultTarget: TargetClass, enterTarget: TargetClass, editInfo: EditInfo): String {
            var methodName = "to${resultTarget.className}"
            //搜寻方法是否存在,存在则提示替换
            var index = 1;
            while (editInfo.psiClass.findMethodsByName(methodName, false).isNotEmpty()) {
                methodName = methodName + index
                index++
            }

            val str = StringBuilder()
            str.append("public ")
            when (resultTarget.type) {
                SimpleUtil.ARR -> str.append("${resultTarget.className}[]")
                SimpleUtil.COLLECT -> str.append("List<${resultTarget.className}>")
                else -> str.append(resultTarget.className)
            }
            str.append(" $methodName(")
            when (resultTarget.type) {
                SimpleUtil.ARR -> str.append("${enterTarget.className}[] ${enterTarget.paramName}")
                SimpleUtil.COLLECT -> str.append("List<${enterTarget.className}> ${enterTarget.paramName}")
                else -> str.append("${enterTarget.className} ${enterTarget.paramName}")
            }
            str.append(") {")
            str.append(createMethodStr(resultTarget, enterTarget, editInfo))
            str.append("}")

            val methodFromText = PsiElementFactory.getInstance(editInfo.e.project)
                .createMethodFromText(str.toString(), editInfo.psiClass)
            WriteCommandAction.runWriteCommandAction(editInfo.e.project) {
                editInfo.psiClass.add(methodFromText)
            }

            return methodName
        }
    }
}
