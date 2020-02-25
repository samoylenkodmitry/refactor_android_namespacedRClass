@file:Suppress("ConstantConditionIf")

import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.*
import java.util.concurrent.CountDownLatch
import kotlin.collections.ArrayList
import kotlin.collections.HashMap

val projectDir = "/home/s/develop/projects/ivi4/zengalt-android-ivi/ivi"
val enableSimpleRefactoring = true
val refactorAmbigious = true
val checkSuspiciousResources = false
val warnAboutSkipped = false
val resRegex =
	"[^a-z_A-Z]*(R\\.(id|string|layout|drawable|style|dimen|plurals|menu|bool|array|color|styleable|animator|integer|font|attr|anim)\\.[a-z_A-Z0-9]+)"
		.toRegex()

@Suppress("MemberVisibilityCanBePrivate")
data class Res(
	val rName: String, val name: String, val type: String, val isFile: Boolean, val file: File, val
	module: Module, val lineNum: Int, val line: String
) {
	val simpleRName get() = "R.$type.$name"
	val resDirs = mutableListOf(resDir)
	val flavors = mutableListOf(flavor)
	val files = mutableListOf(file)

	fun addfile(file: File) = files.add(file)
	fun addResDir(resDir: File) = resDirs.add(resDir)
	fun addFlavor(flavor: File) = flavors.add(flavor)
	fun humanRead() = """
{resource $rName from ${module.file.name}
"$line"
$file : $lineNum}
	""".trimIndent()

	val flavor: File get() = file.parentFile.parentFile.parentFile
	val resDir: File get() = file.parentFile
}

data class Module(val file: File, val packageName: String)

val f = File(projectDir)
val modules = hashMapOf<File, Module>()
f.walk()
	.filter { it.name == "AndroidManifest.xml" }
	.forEach { manifest ->
		val packageName = manifest.readLines().first { it.contains("package=\"") }.cut("package=\"", "\"")
		val module = manifest.parentFile.parentFile.parentFile
		val m = modules[module]
		if (m == null) {
			modules[module] = Module(module, packageName)
		}
	}

val latch = CountDownLatch(modules.size)
val allRes: MutableList<Res> = Collections.synchronizedList(ArrayList<Res>())
for (module in modules) {
	val otherModulesNames = modules.filter { it != module }.map { it.key.absolutePath + "/" }.toHashSet()

	Thread {

		//println("${module.key} -- ${module.value.packageName}")
		module.key.walk()
			.filter { it.name.endsWith(".xml") || it.name.endsWith(".png") || it.name.endsWith(".jpg") }
			.filter { it.name != "AndroidManifest.xml" }
			.filter { !it.path.contains(".idea") }
			.filter { !it.path.contains("/assets/") }
			.filter { !it.path.contains("/config/") }
			.filter { !it.path.contains(".gradle") }
			.filter { !it.name.endsWith("shortcuts.xml") }
			.filter { !it.name.endsWith("developer_options.xml") }
			.filter { !it.name.endsWith("network_security_config.xml") }
			.filter { file ->
				!otherModulesNames.any { otherModule ->
					module.key.absolutePath.length < otherModule.length && file.absolutePath.contains(otherModule)
				}
			}
			.forEach { file ->
				if (file.path.contains("/res/layout")) {
					val name = file.name.substringBefore(".xml")
					allRes += res("layout", module, name, file, 0, file.name, true)
				}
				when {
					file.path.contains("/res/drawable") -> {
						val name = file.name.substringBefore(".")
						allRes += res("drawable", module, name, file, 0, file.name, true)
					}
					file.path.contains("/res/mipmap") -> {
						val name = file.name.substringBefore(".")
						allRes += res("mipmap", module, name, file, 0, file.name, true)
					}
					file.path.contains("/res/animator") -> {
						val name = file.name.substringBefore(".xml")
						allRes += res("animator", module, name, file, 0, file.name, true)
					}
					file.path.contains("/res/anim") -> {
						val name = file.name.substringBefore(".xml")
						allRes += res("anim", module, name, file, 0, file.name, true)
					}
					file.path.contains("/res/transition") -> {
						val name = file.name.substringBefore(".xml")
						allRes += res("transition", module, name, file, 0, file.name, true)
					}
					file.path.contains("/res/menu") -> {
						val name = file.name.substringBefore(".xml")
						allRes += res("menu", module, name, file, 0, file.name, true)
					}
					else -> {
						var lineNum = -1
						file.forEachLine { line ->
							lineNum++
							val trim = line.trim()
							@Suppress("ControlFlowWithEmptyBody")
							if (trim.startsWith("//") || trim.startsWith("<!--") && trim.endsWith("-->")) {
								//skip
								return@forEachLine
							}
							line.split("</").forEach {
								if (it.substringAfter("name=\"").contains("name=\"")) {
									//many resources in one line
									println("*** possible lost resource when there are many in one line:$it")
								}
								@Suppress("ControlFlowWithEmptyBody")
								if (it.contains("android:id=\"@+id/")) {
									val name = it.cut("android:id=\"@+id/", "\"")
									allRes += res("id", module, name, file, lineNum, it)
								} else if (it.contains("<string name=\"")) {
									val name = it.cut("<string name=\"", "\"")
									allRes += res("string", module, name, file, lineNum, it)
								} else if (it.contains("<integer name=\"")) {
									val name = it.cut("<integer name=\"", "\"")
									allRes += res("integer", module, name, file, lineNum, it)
								} else if (it.contains("<style name=\"")) {
									val name = it.cut("<style name=\"", "\"")
									allRes += res("style", module, name, file, lineNum, it)
								} else if (it.contains("<dimen name")) {
									val name = it.cut("=\"", "\"")
									allRes += res("dimen", module, name, file, lineNum, it)
								} else if (it.contains("<attr name=\"") && !it.contains("android:")) {
									val name = it.cut("<attr name=\"", "\"")
									allRes += res("attr", module, name, file, lineNum, it)
								} else if (it.contains("<string-array name=\"")) {
									val name = it.cut("<string-array name=\"", "\"")
									allRes += res("stringarray", module, name, file, lineNum, it)
								} else if (it.contains("<color name=\"")) {
									val name = it.cut("<color name=\"", "\"")
									allRes += res("color", module, name, file, lineNum, it)
								} else if (it.contains("<bool name=\"")) {
									val name = it.cut("<bool name=\"", "\"")
									allRes += res("bool", module, name, file, lineNum, it)
								} else if (it.contains("<plurals name=\"")) {
									val name = it.cut("<plurals name=\"", "\"")
									allRes += res("plurals", module, name, file, lineNum, it)
								} else if (it.contains("<drawable name=\"")) {
									val name = it.cut("<drawable name=\"", "\"")
									allRes += res("drawable", module, name, file, lineNum, it)
								} else if (it.contains("<integer-array name=\"")) {
									val name = it.cut("<integer-array name=\"", "\"")
									allRes += res("integerarray", module, name, file, lineNum, it)
								} else if (it.contains("<array name=\"")) {
									val name = it.cut("<array name=\"", "\"")
									allRes += res("array", module, name, file, lineNum, it)
								} else if (it.contains("android:") || it.contains("app:") || it.contains("<?xml")
									|| it.contains("resources") || it.contains("--")
									|| it.contains("<item name=")
									|| it.contains("<enum name=")
									|| it.contains("declare-styleable")
									|| it.contains("tools:")
									|| it.contains("style>")
									|| it.contains("attr>")
									|| it.contains("set>")
									|| it.contains("item>")
									|| it.contains("string>")
									|| it.contains("integer>")
									|| it.contains("dimen>")
									|| it.contains("color>")
									|| it.contains("bool>")
									|| it.contains("drawable>")
									|| it.contains("plurals>")
									|| it.contains("array>")
									|| it.contains("<item")
									|| it.contains("<flag")
									|| it.contains("<menu")
									|| it.contains("selector>")
									|| it.contains("Row>")
									|| it.contains("Preference")
									|| it.isBlank()
									|| it.contains("xmlns")) {
									//skip
								} else {
									//check other unprocessed resources by uncommenting
									//println(it + " ***" + file.absolutePath)
								}
							}
						}
					}
				}
			}

		latch.countDown()
	}.start()
}
latch.await()

val groupByPackage = HashMap<String, Res>()
for (r in allRes) {
	if (groupByPackage.contains(r.rName)) {
		val rr = groupByPackage[r.rName]!!
		if (rr.resDir != r.resDir) {
			rr.addResDir(r.resDir)
		}
		if (rr.flavor != r.flavor) {
			rr.addFlavor(r.flavor)
		}
		if (r.file == rr.file) {
			println("*** warning! duplicate resources r1=$r r2=$rr")
		}
		rr.addfile(r.file)
	} else {
		groupByPackage[r.rName] = r
	}
}
println("total resources count : " + groupByPackage.size)

val groupBySimpleName = HashMap<String, ArrayList<Res>>()
for (entry in groupByPackage) {
	val key = entry.value.simpleRName
	if (groupBySimpleName.contains(key)) {
		val list = groupBySimpleName[key]!!
		list.add(entry.value)
	} else {
		val value = ArrayList<Res>()
		value.add(entry.value)
		groupBySimpleName[key] = value
	}
}
println("total unique resource ids: " + groupBySimpleName.keys.size)
//suspicious resources!
if (checkSuspiciousResources) {

	groupBySimpleName.entries.stream().filter { it.value.size > 1 }.forEach {
		println("***" + it.key + " " + it.value.toString() + "***")
	}
}
//now find usages
val latchUsages = CountDownLatch(modules.size)
for (module in modules) {
	val otherModulesNames = modules.filter { it != module }.map { it.key.absolutePath + "/" }.toHashSet()

	Thread {
		val modulePackage = module.value.packageName

		module.key.walk()
			.filter { it.name.endsWith(".kt") || it.name.endsWith(".java") }
			.filter { file ->
				!otherModulesNames.any { otherModule ->
					module.key.absolutePath.length < otherModule.length && file.absolutePath.contains(otherModule)
				}
			}
			.forEach { file ->
				val isKt = file.name.endsWith(".kt")
				val fileText = file.readText()
				val rImportPackage = fileText.lines().filter { line ->
					if (line.startsWith("import")) {
						when {
							isKt && line.trim().endsWith(".R") -> return@filter true
							!isKt && line.trim().endsWith(".R;") -> return@filter true
						}
					}
					return@filter false
				}.map { it.trim().substringAfter("import ").substringBefore(".R") }
					.firstOrNull()

				if (!rImportPackage.isNullOrBlank()) {

					var replaced = false
					val newText = fileText.lines()
						.mapIndexed { lineNum, line ->
							if (line.contains(".R") || !line.contains("R.")) {

								return@mapIndexed line
							}
							var newLine = line
							resRegex.findAll(line)
								.forEachIndexed { _, match ->
									val simpleResName = match.groupValues[1]
									val ress = groupBySimpleName[simpleResName]
									if (ress == null) {

										if (warnAboutSkipped)
											println(
												"*** can't find resource, skip" + simpleResName + " " + rImportPackage +
													" " + file.absolutePath
											)
									} else {
										if (ress.size == 1) {
											val res = ress[0]
											if (res.rName.startsWith(modulePackage)) {
												if (warnAboutSkipped)
													println(
														"res has same package as module, skip: " + simpleResName + " " +
															rImportPackage + "" +
															" " + modulePackage + " " + file.absolutePath
													)
											} else {
												//simple refactor
												//println(simpleResName + " " + res.humanRead() + " " + file .absolutePath)
												replaced = true
												newLine = newLine.replace(simpleResName, res.rName)
											}
										} else {
											//try to find same package res
											val samePackageRes =
												ress.filter { it.module.packageName == modulePackage }
													.firstOrNull()
											if (samePackageRes != null) {
												if (rImportPackage == modulePackage) {
													//same package res exist
													if (warnAboutSkipped)
														println(
															"res has same package as module, skip: " + simpleResName + " " +
																rImportPackage + "" +
																" " + modulePackage + " " + file.absolutePath
														)
												} else {
													if (refactorAmbigious) {
														//ambigious refactor
														val res = printAmbigiousRefactor(
															simpleResName,
															line,
															file,
															lineNum,
															rImportPackage,
															ress
														)
														if (res != null) {

															if (enableSimpleRefactoring) {
																replaced = true
																newLine = newLine.replace(simpleResName, res.rName)
															}
														}
													}
												}

											} else {
												//ambigious refactor
												if (refactorAmbigious) {
													val res = printAmbigiousRefactor(
														simpleResName,
														line,
														file,
														lineNum,
														rImportPackage,
														ress
													)
													if (res != null) {

														replaced = true
														newLine = newLine.replace(simpleResName, res.rName)
													}
												}
											}
										}
									}
								}

							return@mapIndexed newLine
						}.joinToString("\n")
					if (replaced) {
						//println("refactoring " + file.absolutePath)
						//println(newText)
						if (enableSimpleRefactoring || refactorAmbigious)
							file.writeText(newText)
					}
				}
			}
		latchUsages.countDown()
	}.start()
}
latchUsages.await()
println("finish")
fun res(
	type: String,
	module: MutableMap.MutableEntry<File, Module>,
	name: String,
	file: File,
	lineNum: Int,
	line: String,
	isFile: Boolean = false
) = Res(
	module.value.packageName + ".R." + type + "." + name,
	name,
	type,
	isFile,
	file,
	module.value,
	lineNum, line
)

fun String.cut(from: String, to: String) = this.substringAfter(from).substringBefore(to)

@Synchronized
fun printAmbigiousRefactor(
	simpleResName: String,
	line: String,
	file: File,
	lineNum: Int,
	rImportPackage: String?,
	ress: ArrayList<Res>
): Res? {
	println(
		"""
ambigious use $simpleResName
line: "$line"
file: $file : $lineNum
file import line: ${rImportPackage}.R
candidates:

${ress.mapIndexed { index, res -> "$index ${res.humanRead()}\n" }}

Print index num "${(0 until ress.size).joinToString(",")}" or skip

""".trimIndent()
	)
	val userChoose = BufferedReader(InputStreamReader(System.`in`)).readLine()
	val option = userChoose.toIntOrNull()
	if (option != null && option in (0 until ress.size)) {
		println("You choose " + option + " " + ress[option].humanRead() + "\n\n\n")
		return ress[option]
	} else {

		println("skipping")
		return null
	}
}