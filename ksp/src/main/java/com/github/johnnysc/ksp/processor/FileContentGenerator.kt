package com.github.johnnysc.ksp.processor

import com.google.devtools.ksp.symbol.KSClassDeclaration
import java.io.File

/**
 * @author Asatryan on 03.05.2023
 */
class FileContentGenerator(private val codeGen: CodeGen = CodeGen()) {

    fun generateFiles(listSymbols: List<KSClassDeclaration>): List<CodeGen.FileNameAndContent> {
        val finalList = mutableListOf<CodeGen.FileNameAndContent>()
        val list = listSymbols.mapNotNull { it.containingFile }
        list.forEach { ksFile ->
            val source = File(ksFile.filePath).readText()
            val codeList = codeGen.parse(source)
            finalList.addAll(codeList)
        }
        return finalList
    }
}

