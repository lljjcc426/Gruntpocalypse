package net.spartanb312.grunteon.obfuscator.process.hierarchy2

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap
import it.unimi.dsi.fastutil.ints.IntArrayList
import it.unimi.dsi.fastutil.ints.IntArraySet
import it.unimi.dsi.fastutil.ints.IntOpenHashSet
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap
import it.unimi.dsi.fastutil.objects.ObjectArrayList
import net.spartanb312.genesis.kotlin.extensions.*
import org.objectweb.asm.tree.FieldNode
import java.util.function.ToIntFunction

data class FieldNodeKey(
    val name: String,
    val desc: String
)

class FieldHierarchy(
    val classHierarchy: ClassHierarchy,
    val fieldNodes: Array<FieldNode>,
    val fieldOwner: IntArray,
    val classNodeMethodLookup: Array<Object2IntOpenHashMap<FieldNodeKey>>,
    val sourceField: BooleanArray,
) {
    fun findField(className: String, fieldName: String, fieldDesc: String): Int {
        val classIdx = classHierarchy.findClass(className)
        if (classIdx == -1) return -1
        return findField(classIdx, fieldName, fieldDesc)
    }

    fun findField(classIdx: Int, fieldName: String, fieldDesc: String): Int {
        val fieldLookup = classNodeMethodLookup[classIdx]
        val fieldKey = FieldNodeKey(fieldName, fieldDesc)
        return fieldLookup.getInt(fieldKey)
    }

    fun isSourceField(fieldIdx: Int): Boolean {
        return sourceField[fieldIdx]
    }

    companion object {
        @Suppress("UNCHECKED_CAST")
        fun build(classHierarchy: ClassHierarchy): FieldHierarchy {
            val fieldNodes = ObjectArrayList<FieldNode>(classHierarchy.realClassCount)
            val fieldOwner = IntArrayList(classHierarchy.realClassCount)
            val classFieldNodeLookup = Array(classHierarchy.realClassCount) {
                Object2IntOpenHashMap<FieldNodeKey>().apply {
                    defaultReturnValue(-1)
                }
            }
            val classToField = arrayOfNulls<IntArrayList>(classHierarchy.realClassCount) as Array<IntArrayList>

            fun populateFieldNodesAndOwners() {
                for (i in 0..<classHierarchy.realClassCount) {
                    val classNode = classHierarchy.classNodes[i]
                    val fields = classNode.fields ?: emptyList()
                    val fieldList = IntArrayList()
                    classToField[i] = fieldList
                    val fieldLookup = classFieldNodeLookup[i]
                    fieldLookup.defaultReturnValue(-1)
                    for (j in fields.indices) {
                        val fieldNode = fields[j]
                        val index = fieldNodes.size
                        fieldNodes.add(fieldNode)
                        val key = FieldNodeKey(fieldNode.name, fieldNode.desc)
                        fieldLookup[key] = index
                        fieldOwner.add(i)
                        fieldList.add(index)
                    }
                }
            }
            populateFieldNodesAndOwners()

            val fieldCount = fieldNodes.size
            val fieldCodeLookup = Object2IntOpenHashMap<String>(fieldCount).apply {
                defaultReturnValue(-1)
            }
            val fieldCode = IntArray(fieldCount)
            val fieldAccess = IntArray(fieldCount)
            val fieldToFieldTree = Array(classHierarchy.realClassCount) {
                Int2ObjectOpenHashMap<IntArraySet>()
            } // Tells a class's field code belongs to which field tree(s)

            fun assignFieldCodeAndBroadcastToDescendants() {
                for (fieldIdx in 0..<fieldCount) {
                    val fieldNode = fieldNodes[fieldIdx]
                    val codename = fieldNode.name + fieldNode.desc
                    val myFieldCode = fieldCodeLookup.computeIfAbsent(codename, ToIntFunction {
                        fieldCodeLookup.size
                    })
                    fieldCode[fieldIdx] = myFieldCode
                    fieldAccess[fieldIdx] = fieldNode.access

                    // Fill inherent field bits
                    if (fieldNode.access.isPrivate) continue
                    val fieldOwnerIdx = fieldOwner.getInt(fieldIdx)
                    fieldToFieldTree[fieldOwnerIdx].put(myFieldCode, IntArraySet())
                    val descendents = classHierarchy.descendants[fieldOwnerIdx]
                    for (i in 0..<descendents.size) {
                        val descendentIdx = descendents[i]
                        if (descendentIdx < classHierarchy.realClassCount) {
                            fieldToFieldTree[descendentIdx].put(myFieldCode, IntArraySet())
                        }
                    }
                }
            }
            assignFieldCodeAndBroadcastToDescendants()

            // Search up for source field
            val isSourceField = BooleanArray(fieldCount)

            fun assignFieldTreeToFields() {
                for (classIdx in 0..<classHierarchy.realClassCount) {
                    fun setSource(fieldIdx: Int) {
                        assert(!isSourceField[fieldIdx])
                        isSourceField[fieldIdx] = true
                    }

                    val myFields = classToField[classIdx]
                    val myFieldArray = myFields.elements()
                    for (j in 0..<myFields.size) {
                        val myField = myFieldArray[j]
                        if (!fieldAccess[myField].isPrivate) continue
                        setSource(myField)
                    }

                    val parentIndices = classHierarchy.parents[classIdx]
                    val allParentFieldCodeBits = IntOpenHashSet()
                    for (i in 0..<parentIndices.size) {
                        val parentIdx = parentIndices[i]
                        if (parentIdx >= classHierarchy.realClassCount) continue
                        val parentCodeBits = fieldToFieldTree[parentIdx]
                        allParentFieldCodeBits.addAll(parentCodeBits.keys)
                    }

                    for (j in 0..<myFields.size) {
                        val myField = myFieldArray[j]
                        if (fieldAccess[myField].isPrivate) continue
                        val myFieldCode = fieldCode[myField]
                        if (!allParentFieldCodeBits.contains(myFieldCode)) {
                            setSource(myField)
                        }
                    }
                }
            }

            assignFieldTreeToFields()

            return FieldHierarchy(
                classHierarchy,
                fieldNodes.toTypedArray(),
                fieldOwner.toIntArray(),
                classFieldNodeLookup,
                isSourceField
            )
        }
    }
}