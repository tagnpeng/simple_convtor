package com.simple.demo5

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.PsiElementFactory
import com.simple.simpleconvert.SimpleUtil
import java.util.stream.Collectors

class CreateMethod {
    companion object {
        /**
         * @param [resultTarget] 返回类
         * @param [enterTarget] 输入类
         * @param [editInfo]
         * @return [String] 返回转换字符串
         */
        fun createMethodStr(resultTarget: TargetClass, enterTarget: TargetClass, editInfo: EditInfo): String {
            if (resultTarget.type != enterTarget.type) {
                throw Exception(resultTarget.paramName + "与" + enterTarget.paramName + "类型不一致(" + resultTarget.type + "," + enterTarget.type + ")");
            }
            return createMethodHeard(resultTarget, enterTarget, editInfo)
        }

        /**
         *  创建方法头信息,一共两种
         *         if (man == null) {
         *             return null;
         *         }
         *         if (simple == null) {
         *             return ArrayList()
         *         }
         * @param [resultTarget]
         * @param [enterTarget]
         * @param [editInfo]
         * @return [String]
         */
        fun createMethodHeard(resultTarget: TargetClass, enterTarget: TargetClass, editInfo: EditInfo): String {
            val str = StringBuffer();
            if (enterTarget.type == SimpleUtil.OBJECT || enterTarget.type == SimpleUtil.ARR) {
                str.append("if(").append(enterTarget.paramName).append("==null){return null;}")
            } else if (enterTarget.type == SimpleUtil.COLLECT) {
                str.append("if(").append(enterTarget.paramName).append("==null){ return new ArrayList();}")
            }
            str.append(createMethodMain(resultTarget, enterTarget, editInfo))
            return str.toString();
        }

        /**
         * 创建方法主体,一共三种
         *         SImpleB[] list = new SImpleB[simles.length];
         *         for (int i = 0; i < simles.length; i++) {
         *             simles[i];
         *             SImpleB sImpleB = new SImpleB();
         *             sImpleB.setA(simles[i].getA());
         *             list[i] = sImpleB;
         *         }
         *         return list;
         *
         *         SImpleB sImpleB = new SImpleB();
         *         sImpleB.setName(simple.getName());
         *         sImpleB.setDate(simple.getDate());
         *         sImpleB.setA(simple.getA());
         *         return sImpleB;
         *
         *         List<SImpleB> list = new ArrayList<>();
         *         for (Simple simple : simpleList) {
         *             SImpleB sImpleB = new SImpleB();
         *             sImpleB.setName(simple.getName());
         *             sImpleB.setDate(simple.getDate());
         *             sImpleB.setA(simple.getA());
         *             list.add(sImpleB);
         *         }
         *         return list;
         * @param [resultTarget]
         * @param [enterTarget]
         * @param [editInfo]
         * @return [String]
         */
        private fun createMethodMain(resultTarget: TargetClass, enterTarget: TargetClass, editInfo: EditInfo): String {
            val str = StringBuffer();
            if (resultTarget.type == SimpleUtil.ARR) {
                str.append(resultTarget.className).append("[] list=new ").append(resultTarget.className).append("[")
                    .append(enterTarget.paramName).append(".length];");
                str.append("for(int i=0;i<").append(enterTarget.paramName).append(".length;i++){")
                str.append(enterTarget.className).append(" ").append("iterationItem").append("=")
                    .append(enterTarget.paramName).append("[i];")
                str.append(createAssignment("iterationItem", resultTarget, enterTarget, editInfo))
                str.append("list.add(iterationItem);")
                str.append("}")
                str.append("return list;")
            } else if (resultTarget.type == SimpleUtil.COLLECT) {
                str.append("List<").append(resultTarget.className).append("> list=new ArrayList<")
                    .append(resultTarget.className).append(">();")
                str.append("for(").append(enterTarget.className).append(" ").append("iterationItem")
                    .append(":").append(enterTarget.paramName).append("){")
                str.append(createAssignment("iterationItem", resultTarget, enterTarget, editInfo))
                str.append("list.add(iterationItem);")
                str.append("}")
                str.append("return list;")
            } else {
                str.append(createAssignment(enterTarget.paramName!!, resultTarget, enterTarget, editInfo))
                str.append("return ").append(resultTarget.paramName).append(";")
            }
            return str.toString();
        }

        private fun createAssignment(
            name: String,
            resultTarget: TargetClass,
            enterTarget: TargetClass,
            editInfo: EditInfo
        ): String {
            val str = StringBuffer();
            val paramFieldToMap: Map<String, TargetField> = enterTarget.targetField!!.stream()
                .collect(
                    Collectors.toMap(
                        { item: TargetField -> item.fieldName },
                        { item: TargetField -> item }
                    )
                )

            str.append(resultTarget.className).append(" ").append(resultTarget.paramName).append("=new ")
                .append(resultTarget.className).append("();")
            resultTarget.targetField!!.forEach { item ->
                run {
                    str.append(resultTarget.paramName).append(".").append(item.method).append("(")
                    //判断传入的参数是凑存在同等属性
                    if (paramFieldToMap.containsKey(item.fieldName)) {
                        if (item.type == SimpleUtil.ARR || item.type == SimpleUtil.COLLECT || item.type == SimpleUtil.OBJECT) {
                            //创建新方法
                            val methodName = createNewMethod(
                                item.targetClass!!,
                                paramFieldToMap[item.fieldName]!!.targetClass!!,
                                editInfo
                            )
                            str.append(methodName).append("(").append(enterTarget.paramName).append(".")
                                .append(paramFieldToMap[item.fieldName]!!.method)
                                .append("())")
                        } else {
                            str.append(name).append(".").append(paramFieldToMap[item.fieldName]!!.method)
                                .append("()")
                        }
                    } else {
                        str.append("null")
                    }
                    str.append(");")
                }
            }
            return str.toString()
        }

        /**
         * 生成新方法
         * @param [resultTarget]
         * @param [enterTarget]
         * @return [String] 方法名称
         */
        private fun createNewMethod(resultTarget: TargetClass, enterTarget: TargetClass, editInfo: EditInfo): String {
            var methodName = "to" + resultTarget.className;
            val str = StringBuffer();
            str.append("public ")
            if (resultTarget.type == SimpleUtil.ARR) {
                str.append(resultTarget.className).append("[]")
            } else if (enterTarget.type == SimpleUtil.COLLECT) {
                str.append("List<").append(resultTarget.className).append(">")
            } else {
                str.append(resultTarget.className)
            }
            str.append(" ").append(methodName).append("(")
            if (resultTarget.type == SimpleUtil.ARR) {
                str.append(enterTarget.className).append("[] ").append(enterTarget.paramName)
            } else if (enterTarget.type == SimpleUtil.COLLECT) {
                str.append("List<").append(enterTarget.className).append("> ").append(enterTarget.paramName)
            } else {
                str.append(enterTarget.className).append(" ").append(enterTarget.paramName)
            }
            str.append(")").append("{")
            str.append(createMethodStr(resultTarget, enterTarget, editInfo))
            str.append("}")
            val methodFromText = PsiElementFactory.getInstance(editInfo.e.project)
                .createMethodFromText(str.toString(), editInfo.psiClass)
            WriteCommandAction.runWriteCommandAction(editInfo.e.project) {
                editInfo.psiClass.add(methodFromText)
            }

            return methodName;
        }
    }
}