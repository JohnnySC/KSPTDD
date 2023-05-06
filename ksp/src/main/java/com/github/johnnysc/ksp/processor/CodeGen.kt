package com.github.johnnysc.ksp.processor

class CodeGen {

    data class FileNameAndContent(
        val fileName: String,
        val packageName: String,
        val content: String
    )

    fun parse(source: String): List<FileNameAndContent> {
        val lines = ("$source\nEOF").split("\n")
        val classLine = lines.find { it.startsWith("class") }!!

        val beforeBlockBuilder = java.lang.StringBuilder()
        var beforeBlock = ""
        var beforeBlockStarted = false
        var beforeBlockFinished = false

        val testsBlockList = mutableListOf<String>()
        var testStarted = false
        var testFinished = false
        var currentTest = ""

        val privateInterfacesList = mutableListOf<String>()
        var currentPrivateInterface = ""
        var privateInterfaceBlockStarted = false
        val privateLateInitVars = mutableListOf<String>()

        lines.forEachIndexed { index, s ->
            val line = s.trim()
            if (!beforeBlockStarted && line.startsWith("private lateinit var")) {
                privateLateInitVars.add(line)
            } else if (line.startsWith("@Before")) {
                beforeBlockStarted = true
                beforeBlockBuilder.append(s)
                beforeBlockBuilder.append("\n")
            } else if (line.startsWith("@Test")) {
                if (!beforeBlockFinished) {
                    beforeBlock = beforeBlockBuilder.toString()
                    beforeBlockFinished = true
                }

                testStarted = true
                if (currentTest.isNotEmpty())
                    testsBlockList.add(currentTest)
                currentTest = s + "\n"
            } else if (line.startsWith("private interface")) {
                privateInterfaceBlockStarted = true
                if (!testFinished && currentTest.isNotEmpty()) {
                    testsBlockList.add(currentTest)
                    currentTest = ""
                    testFinished = true
                }

                if (currentPrivateInterface.isNotEmpty()) {
                    privateInterfacesList.add(currentPrivateInterface)
                }
                currentPrivateInterface = s + "\n"
            } else {
                if (beforeBlockStarted && !beforeBlockFinished) {
                    beforeBlockBuilder.append(s)
                    beforeBlockBuilder.append("\n")
                }
                if (testStarted && !testFinished) {
                    currentTest += s
                    currentTest += "\n"
                }
                if (privateInterfaceBlockStarted) {
                    currentPrivateInterface += s + "\n"
                }

                if (line.contains("EOF")) {
                    privateInterfacesList.add(currentPrivateInterface)
                }
            }
        }

        val packageLine = PackageLine(lines[0])
        val classLineObject = ClassLine(classLine)
        val privateLateinitVars = PrivateLateinitVars(privateLateInitVars, classLineObject)
        val beforeBlockObject = BeforeBlock(beforeBlock, classLineObject)
        val testFuns = TestFunBlocks(testsBlockList, privateLateinitVars)
        val privateInterfacesListObject = PrivateInterfacesListObject(privateInterfacesList)

        val list = mutableListOf<String>()

        privateInterfacesListObject.dependencies.forEach {
            val textBuilder = StringBuilder()
            textBuilder.append("package ")
            textBuilder.append(packageLine.starting)
            textBuilder.append(".")
            textBuilder.append(it.finalPackage)
            textBuilder.append("\n\n")
            textBuilder.append("interface ")
            textBuilder.append(it.interfaceName)
            textBuilder.append(" {\n\n")
            it.funs.forEach { funName ->
                textBuilder.append("    ")
                textBuilder.append(funName)
                textBuilder.append("\n")
            }
            textBuilder.append("}")

            list.add(textBuilder.toString())
        }

        val classBuilder = StringBuilder()

        classBuilder.append(packageLine.rawValue)
        classBuilder.append("\n\n")

        val dependencies =
            privateInterfacesListObject.dependencies.filter { it.finalPackage != packageLine.ending }
        dependencies.forEach {
            classBuilder.append("import ")
            classBuilder.append(packageLine.starting)
            classBuilder.append('.')
            classBuilder.append(it.finalPackage)
            classBuilder.append('.')
            classBuilder.append(it.interfaceName)
            classBuilder.append("\n\n")
        }

        classBuilder.append("class ")
        classBuilder.append(classLineObject.testingClassName)
        classBuilder.append("(\n")
        beforeBlockObject.argsAndNames.forEachIndexed { index, argAndName ->
            classBuilder.append("    private val ")
            classBuilder.append(argAndName.argName)

            val fakeClassName =
                privateLateinitVars.dependencies.find { argAndName.value == it.name }!!
            val dependency =
                privateInterfacesListObject.dependencies.find { it.fakeClassName == fakeClassName.type }!!
            classBuilder.append(": ")
            classBuilder.append(dependency.interfaceName)
            if (index + 1 < beforeBlockObject.argsAndNames.size)
                classBuilder.append(",")
            classBuilder.append("\n")
        }
        classBuilder.append(") {\n\n")

        testFuns.linesWithFunCalls.forEach {
            classBuilder.append("    fun ")
            classBuilder.append(it)
            classBuilder.append(" {\n")
            classBuilder.append("        //todo\n")
            classBuilder.append("    }\n\n")
        }
        classBuilder.append("}")

        val testingClass = classBuilder.toString()
        list.add(testingClass)

        return list.map {
            val theLines = it.split("\n")
            val firstLine = theLines[0]
            val packageName = firstLine.substring("package ".length)
            var fileName = ""
            theLines.find { line -> line.startsWith("interface ") }?.let { foundInterfaceLine ->
                fileName = foundInterfaceLine.substring(
                    "interface ".length,
                    foundInterfaceLine.indexOf("{")
                ).trim()
            }
            if (fileName.isEmpty()) theLines.find { line -> line.startsWith("class ") }
                ?.let { foundClassLine ->
                    fileName = foundClassLine.substring(
                        "class ".length,
                        foundClassLine.indexOf("(")
                    ).trim()
                }

            FileNameAndContent(fileName, packageName, it)
        }
    }

    private data class PackageLine(val rawValue: String) {
        val value = rawValue.substring("package ".length)
        private val lastIndexOfDot = value.lastIndexOf('.')
        val starting = value.substring(0, lastIndexOfDot)
        val ending = value.substring(lastIndexOfDot + 1)
    }

    private data class ClassLine(val rawValue: String) {
        val value = rawValue.split(" ")[1]
        val testingClassName = value.substring(0, value.length - "test".length)
    }

    private data class PrivateLateinitVars(val rawValues: List<String>, val classLine: ClassLine) {
        private val nameAndTypeList = rawValues.map {
            val nameAndType = it.substring("private lateinit var ".length)
            val parts = nameAndType.split(':')
            val name = parts[0].trim()
            val type = parts[1].trim()
            PrivateProperty(name, type)
        }

        val testingObjectPropertyName =
            nameAndTypeList.find { !it.isDependency(classLine) }?.name ?: ""

        val dependencies = nameAndTypeList.filter { it.isDependency(classLine) }

        data class PrivateProperty(val name: String, val type: String) {
            fun isDependency(classLine: ClassLine) = type != classLine.testingClassName
        }
    }

    private data class BeforeBlock(val value: String, val classLine: ClassLine) {
        private val raw: String = value.substring(value.indexOf(classLine.testingClassName))
        private val constructorInnerPart =
            raw.substring(raw.indexOf("(") + 1, raw.indexOf(")")).replace("\n", "")
        private val dependenciesList =
            (if (constructorInnerPart.contains(','))
                constructorInnerPart.split(',')
            else
                listOf(constructorInnerPart)
                    ).map { it.trim() }.filter { it.isNotEmpty() }
        val argsAndNames = dependenciesList.map {
            val parts = it.split('=')
            ConstructorArgAndName(parts[0].trim(), parts[1].trim())
        }

        data class ConstructorArgAndName(val argName: String, val value: String)
    }

    private data class TestFunBlocks(
        val rawValue: List<String>,
        val privateLateinitVars: PrivateLateinitVars
    ) {
        var linesWithFunCalls: MutableList<String> = mutableListOf() // init() retry()

        init {
            rawValue.forEach { raw ->
                raw.split("\n").forEach {
                    val nameAndDot = privateLateinitVars.testingObjectPropertyName + "."
                    if (it.contains(nameAndDot))
                        linesWithFunCalls.add(it.trim().substring(nameAndDot.length))
                }
            }
            linesWithFunCalls = linesWithFunCalls.toSet().toMutableList()
        }
    }

    private data class PrivateInterfacesListObject(
        val rawValues: List<String>,
    ) {

        val dependencies = mutableListOf<Dependency>()

        init {
            rawValues.forEach { raw ->
                val lines = raw.split("\n").map { it.trim() }
                val firstLine = lines[0]
                val fakeClassName =
                    firstLine.substring("private interface ".length, firstLine.indexOf(':')).trim()
                val name =
                    firstLine.substring(firstLine.indexOf(':') + 1, firstLine.indexOf('{')).trim()
                val funs = mutableListOf<String>()
                val fakeFuns = lines.filter { it.startsWith("fun ") }
                val overridenFuns = lines.filter { it.startsWith("override ") }

                val overridenMapsClear =
                    overridenFuns.map { it.substring("override ".length, it.indexOf("{")).trim() }
                overridenMapsClear.forEach { overriden ->
                    if (!fakeFuns.contains(overriden)) {
                        funs.add(overriden)
                    }
                }
                val finalPackage =
                    if (name.endsWith("Interactor") || name.endsWith("Repository"))
                        "domain"
                    else if (name.endsWith("ViewModel") || name.endsWith("Communication")) {
                        "presentation"
                    } else if (name.endsWith("DataSource")) {
                        "data"
                    } else
                        "other"
                val dependency = Dependency(name, funs, finalPackage, fakeClassName)
                dependencies.add(dependency)
            }
        }

        data class Dependency(
            val interfaceName: String,
            val funs: List<String>,
            val finalPackage: String,
            val fakeClassName: String
        )
    }
}