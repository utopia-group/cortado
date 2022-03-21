package edu.utexas.cs.utopia.cortado.util.soot;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Streams;
import soot.*;
import soot.jimple.*;

import javax.annotation.Nonnull;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Used to build a replica of a class with a new name
 */
public class SootClassReplicator
{
    private final SootClass replicaClass;
    private final ImmutableMap<Unit, Unit> unitToReplicaMap;
    private final ImmutableMap<SootMethod, SootMethod> methodToReplicaMap;
    private final ImmutableMap<SootField, SootField> fieldToReplicaMap;

    /**
     * Replicates the class. Note the class will reset
     * the class hierarchy and any points-to info etc. (see {@link Scene#addClass(SootClass)}).
     *
     * Note also that the class will be set as a library class, not an application class
     *
     * @param sootClass the class
     * @param replicaClassName the new name
     */
    public SootClassReplicator(@Nonnull SootClass sootClass, @Nonnull String replicaClassName)
    {
        final String packageName = sootClass.getPackageName();
        // strip package name if passed
        if(replicaClassName.contains(packageName))
        {
            replicaClassName = replicaClassName.replaceFirst(packageName + ".", "");
        }
        final String fullyQuantifiedReplicaName = packageName + "." + replicaClassName;
        if(Scene.v().containsClass(fullyQuantifiedReplicaName))
        {
            throw new IllegalArgumentException("Class already exists: " + fullyQuantifiedReplicaName);
        }
        // https://www.sable.mcgill.ca/soot/tutorial/createclass/
        final SootClass replicaClass = new SootClass(fullyQuantifiedReplicaName, sootClass.getModifiers());
        replicaClass.setSuperclass(sootClass.getSuperclass());
        sootClass.getInterfaces().forEach(replicaClass::addInterface);
        Scene.v().addClass(replicaClass);

        assert replicaClass.getPackageName().equals(sootClass.getPackageName());

        // add fields
        final FastHierarchy classHierarchy = Scene.v().getOrMakeFastHierarchy();
        final Set<SootField> inductiveFields = sootClass.getFields()
                .parallelStream()
                .filter(field -> !(field.getType() instanceof PrimType))
                .filter(field -> classHierarchy.isSubclass(Scene.v().getSootClass(field.getType().toString()), sootClass))
                .collect(Collectors.toSet());
        if(!inductiveFields.stream().map(SootField::getDeclaringClass).allMatch(sootClass::equals))
        {
            throw new IllegalArgumentException("sootClass has fields of a class which subclasses sootClass");
        }

        Map<SootField, SootField> originalFieldToReplica = new ConcurrentHashMap<>();
        sootClass.getFields()
                .stream()
                .filter(f -> !inductiveFields.contains(f))
                .map(f -> {
                    final SootField replicaField = Scene.v().makeSootField(f.getName(), f.getType(), f.getModifiers());
                    originalFieldToReplica.put(f, replicaField);
                    return replicaField;
                })
                .forEach(replicaClass::addField);
        inductiveFields.stream()
                .map(f -> {
                    final SootField replicaField = Scene.v().makeSootField(f.getName(), replicaClass.getType(), f.getModifiers());
                    originalFieldToReplica.put(f, replicaField);
                    return replicaField;
                })
                .forEach(replicaClass::addField);

        // now copy methods
        final Map<SootMethod, SootMethod> methodToReplica = sootClass.getMethods()
                .stream()
                .collect(Collectors.toMap(Function.identity(), method -> {
                    final List<Type> replicaParameterTypes = method.getParameterTypes()
                            .stream()
                            .map(type -> type.equals(sootClass.getType()) ? replicaClass.getType() : type)
                            .collect(Collectors.toList());
                    final Type replicaReturnType;
                    if (method.getReturnType().equals(sootClass.getType()))
                    {
                        replicaReturnType = replicaClass.getType();
                    } else
                    {
                        replicaReturnType = method.getReturnType();
                    }
                    final SootMethod replicaMethod = new SootMethod(method.getName(),
                            replicaParameterTypes,
                            replicaReturnType,
                            method.getModifiers(),
                            method.getExceptions());
                    replicaClass.addMethod(replicaMethod);

                    if (method.hasActiveBody())
                    {
                        // copy body
                        final Body body = method.getActiveBody();
                        final JimpleBody replicaBody = Jimple.v().newBody(replicaMethod);
                        replicaBody.importBodyContentsFrom(body);
                        final List<Local> localsOfTypeSootClass = replicaBody.getLocals()
                                .stream()
                                .filter(local -> local.getType().equals(sootClass.getType()))
                                .collect(Collectors.toList());
                        // replace locals of old type with locals of new type
                        localsOfTypeSootClass.forEach(localOfTypeSootClass -> {
                            final Local replicaLocal = Jimple.v().newLocal(localOfTypeSootClass.getName(), replicaClass.getType());
                            replicaBody.getLocals().remove(localOfTypeSootClass);
                            replicaBody.getLocals().add(replicaLocal);
                            replicaBody.getUnits()
                                    .stream()
                                    .map(Unit::getUseAndDefBoxes)
                                    .flatMap(Collection::stream)
                                    .filter(valueBox -> valueBox.getValue().equals(localOfTypeSootClass))
                                    .forEach(valueBox -> valueBox.setValue(replicaLocal));
                        });
                        // replace old fields with replica fields
                        replicaBody.getUnits()
                                .stream()
                                .map(Unit::getUseAndDefBoxes)
                                .flatMap(Collection::stream)
                                .filter(valueBox -> valueBox.getValue() instanceof FieldRef)
                                .filter(valueBox -> originalFieldToReplica.containsKey(((FieldRef) valueBox.getValue()).getField()))
                                .forEach(valueBox -> {
                                    final FieldRef fieldRef = (FieldRef) valueBox.getValue();
                                    final FieldRef replicaFieldRef;
                                    if (fieldRef instanceof StaticFieldRef)
                                    {
                                        replicaFieldRef = Jimple.v().newStaticFieldRef(originalFieldToReplica.get(fieldRef.getField()).makeRef());
                                    } else
                                    {
                                        assert fieldRef instanceof InstanceFieldRef;
                                        final SootField newField = originalFieldToReplica.get(fieldRef.getField());
                                        replicaFieldRef = Jimple.v().newInstanceFieldRef(((InstanceFieldRef) fieldRef).getBase(), newField.makeRef());
                                    }
                                    valueBox.setValue(replicaFieldRef);
                                });
                        replicaMethod.setActiveBody(replicaBody);
                    }
                    // also add annotations
                    replicaMethod.addAllTagsOf(method);
                    return replicaMethod;
                }));
        // replace calls to old methods with calls to new method
        replicaClass.getMethods()
                .stream()
                .filter(SootMethod::hasActiveBody)
                .map(SootMethod::getActiveBody)
                .map(Body::getUnits)
                .flatMap(Collection::stream)
                .map(Unit::getUseBoxes)
                .flatMap(Collection::stream)
                .filter(valueBox -> valueBox.getValue() instanceof InvokeExpr)
                .filter(valueBox -> ((InvokeExpr) valueBox.getValue()).getMethod().getDeclaringClass().equals(sootClass))
                .forEach(valueBox -> {
                    InvokeExpr invkExpr = (InvokeExpr) valueBox.getValue();
                    invkExpr.setMethodRef(methodToReplica.get(invkExpr.getMethod()).makeRef());
                });

        // replace calls to new "old class" methods with calls to new "replica"
        replicaClass.getMethods()
                .stream()
                .filter(SootMethod::hasActiveBody)
                .map(SootMethod::getActiveBody)
                .map(Body::getUnits)
                .flatMap(Collection::stream)
                .map(Unit::getUseBoxes)
                .flatMap(Collection::stream)
                .filter(valueBox -> valueBox.getValue() instanceof NewExpr)
                .filter(valueBox -> Scene.v().getSootClass(valueBox.getValue().getType().toString()).equals(sootClass))
                .forEach(valueBox -> valueBox.setValue(Jimple.v().newNewExpr(replicaClass.getType())));

        // replace this-refs with ones to this class
        replicaClass.getMethods()
                .stream()
                .filter(SootMethod::hasActiveBody)
                .filter(m -> !m.isStatic())
                .map(SootMethod::getActiveBody)
                .map(Body::getUnits)
                .flatMap(Collection::stream)
                .map(Unit::getUseAndDefBoxes)
                .flatMap(Collection::stream)
                .filter(vb -> vb.getValue() instanceof ThisRef)
                .forEach(vb -> vb.setValue(Jimple.v().newThisRef(replicaClass.getType())));

        // handle clinit
        final String replicaClassConstantString = "L" + replicaClass.getName().replaceAll("\\.", "/") + ";";
        final String classConstantString = "L" + sootClass.getName().replaceAll("\\.", "/") + ";";
        replicaClass.getMethods()
                .stream()
                .filter(SootMethod::isStaticInitializer)
                .filter(SootMethod::hasActiveBody)
                .map(SootMethod::getActiveBody)
                .map(Body::getUnits)
                .flatMap(Collection::stream)
                .map(Unit::getUseBoxes)
                .flatMap(Collection::stream)
                .filter(valueBox -> valueBox.getValue() instanceof ClassConstant)
                .filter(valueBox -> ((ClassConstant) valueBox.getValue()).value.equals(classConstantString))
                .forEach(valueBox -> valueBox.setValue(ClassConstant.v(replicaClassConstantString)));

        // validate bodies
        replicaClass.getMethods()
                .stream()
                .filter(SootMethod::hasActiveBody)
                .map(SootMethod::getActiveBody)
                .forEach(Body::validate);

        // validate class
        replicaClass.validate();

        // build map from old unit to new unit
        ConcurrentMap<Unit, Unit> unitToReplicaUnitMap = new ConcurrentHashMap<>();
        //noinspection UnstableApiUsage
        Streams.forEachPair(sootClass.getMethods()
                        .parallelStream()
                        .filter(SootMethod::hasActiveBody)
                        .map(SootMethod::getActiveBody)
                        .map(Body::getUnits)
                        .flatMap(Collection::parallelStream),
                replicaClass.getMethods()
                        .parallelStream()
                        .filter(SootMethod::hasActiveBody)
                        .map(SootMethod::getActiveBody)
                        .map(Body::getUnits)
                        .flatMap(Collection::parallelStream),
                unitToReplicaUnitMap::put);
        this.fieldToReplicaMap = ImmutableMap.copyOf(originalFieldToReplica);
        this.methodToReplicaMap = ImmutableMap.copyOf(methodToReplica);
        this.unitToReplicaMap = ImmutableMap.copyOf(unitToReplicaUnitMap);
        this.replicaClass = replicaClass;
    }

    @Nonnull
    public SootClass getReplicaClass()
    {
        return replicaClass;
    }

    @Nonnull
    public ImmutableMap<Unit, Unit> getUnitToReplicaMap()
    {
        return unitToReplicaMap;
    }

    @Nonnull
    public ImmutableMap<SootMethod, SootMethod> getMethodToReplicaMap()
    {
        return methodToReplicaMap;
    }

    public ImmutableMap<SootField, SootField> getFieldToReplicaMap()
    {
        return fieldToReplicaMap;
    }
}
