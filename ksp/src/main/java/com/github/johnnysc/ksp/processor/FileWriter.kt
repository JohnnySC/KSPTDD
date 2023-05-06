package com.github.johnnysc.ksp.processor

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFile
import java.io.OutputStream

/**
 * @author Asatryan on 03.05.2023
 */
class FileWriter(
    private val fileContentGenerator: FileContentGenerator,
    private val codeGenerator: CodeGenerator,
) {

    fun createFile(listedClasses: Sequence<KSClassDeclaration>) {
        if (!listedClasses.iterator().hasNext()) return

        val listSymbols = listedClasses.toList()

        val contentFiles = fileContentGenerator.generateFiles(listSymbols)

        val files = listSymbols.mapNotNull { it.containingFile }
        contentFiles.forEach {
            val file: OutputStream = createFile(files, it.fileName, it.packageName)
            file += it.content
            file.close()
        }
    }

    private fun createFile(
        files: List<KSFile>,
        fileName: String,
        packageName: String
    ) = codeGenerator.createNewFile(
        Dependencies(
            false,
            *files.toList().toTypedArray(),
        ),
        packageName,
        fileName
    )
}

internal operator fun OutputStream.plusAssign(text: String) {
    write(text.toByteArray())
}