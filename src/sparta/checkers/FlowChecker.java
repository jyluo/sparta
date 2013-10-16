package sparta.checkers;

/*>>>
import checkers.compilermsgs.quals.CompilerMessageKey;
*/


import checkers.basetype.BaseTypeChecker;
import checkers.quals.PolyAll;
import checkers.quals.StubFiles;
import checkers.quals.TypeQualifiers;
import checkers.source.SupportedLintOptions;
import checkers.types.QualifierHierarchy;
import checkers.util.AnnotationBuilder;
import checkers.util.MultiGraphQualifierHierarchy;
import checkers.util.MultiGraphQualifierHierarchy.MultiGraphFactory;
import checkers.util.QualifierPolymorphism;
import checkers.util.stub.StubGenerator;

import javacutils.AnnotationUtils;
import javacutils.TreeUtils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.SupportedOptions;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.util.Elements;
import javax.tools.Diagnostic;

import sparta.checkers.quals.FlowPermission;
import sparta.checkers.quals.PolySink;
import sparta.checkers.quals.PolySource;
import sparta.checkers.quals.Sink;
import sparta.checkers.quals.Source;


@TypeQualifiers({ Source.class, Sink.class, PolySource.class, PolySink.class, PolyAll.class })
@StubFiles("information_flow.astub")
@SupportedOptions({ FlowPolicy.POLICY_FILE_OPTION, FlowChecker.MSG_FILTER_OPTION,
        FlowChecker.IGNORE_NOT_REVIEWED })
@SupportedLintOptions({ FlowPolicy.STRICT_CONDITIONALS_OPTION })

public class FlowChecker extends BaseTypeChecker<FlowAnnotatedTypeFactory> {
    public static final String MSG_FILTER_OPTION = "msgFilter";
    public static final String IGNORE_NOT_REVIEWED = "ignorenr";
    public boolean IGNORENR = false;

    protected AnnotationMirror NOSOURCE, ANYSOURCE, POLYSOURCE;
    protected AnnotationMirror NOSINK, ANYSINK, POLYSINK;
    protected AnnotationMirror POLYALL;
    protected AnnotationMirror LITERALSOURCE,  FROMLITERALSINK;
    protected AnnotationMirror NR_SOURCE, NR_SINK;
    protected AnnotationMirror CONDITIONALSINK, FROMCONDITIONALSOURCE;


    protected AnnotationMirror SOURCE;
    protected AnnotationMirror SINK;

    protected FlowPolicy flowPolicy;
    protected Set<String> unfilteredMessages;
    // Methods that are not in a stub file
    protected final Map<String, Map<String, Map<Element, Integer>>> notInStubFile;

    // FlowVisitor uses these to hold flow state
    private FlowAnalyzer flowAnalizer;

    public FlowChecker() {
        super();
        this.notInStubFile = new HashMap<>();
    }

    @Override
    public void initChecker() {

        Elements elements = processingEnv.getElementUtils();
        NOSOURCE = AnnotationUtils.fromClass(elements, Source.class);
        NOSINK = AnnotationUtils.fromClass(elements, Sink.class);

        POLYSOURCE = AnnotationUtils.fromClass(elements, PolySource.class);
        POLYSINK = AnnotationUtils.fromClass(elements, PolySink.class);
        POLYALL = AnnotationUtils.fromClass(elements, PolyAll.class);

        NR_SOURCE = createAnnoFromSource( new TreeSet<FlowPermission>(Arrays.asList(FlowPermission.NOT_REVIEWED)));
        NR_SINK = createAnnoFromSink( new TreeSet<FlowPermission>(Arrays.asList(FlowPermission.NOT_REVIEWED)));

        ANYSOURCE = createAnnoFromSource( new TreeSet<FlowPermission>(Arrays.asList(FlowPermission.ANY)));
        ANYSINK = createAnnoFromSink(  new TreeSet<FlowPermission>(Arrays.asList(FlowPermission.ANY)));

        SOURCE = AnnotationUtils.fromClass(elements, Source.class);
        SINK = AnnotationUtils.fromClass(elements, Sink.class);

        sourceValue = TreeUtils
                .getMethod("sparta.checkers.quals.Source", "value", 0, processingEnv);
        sinkValue = TreeUtils.getMethod("sparta.checkers.quals.Sink", "value", 0, processingEnv);

        super.initChecker();
        // Must call super.initChecker before the lint option can be checked.
        final boolean scArg = getLintOption(FlowPolicy.STRICT_CONDITIONALS_OPTION, false);
        final String pfArg = getOption(FlowPolicy.POLICY_FILE_OPTION);
        if (pfArg == null || pfArg.trim().isEmpty()) {
            flowPolicy = new FlowPolicy(scArg);
        } else {
            flowPolicy = new FlowPolicy(new File(pfArg), scArg);
        }

        final String ignoreArg = getOption(FlowChecker.IGNORE_NOT_REVIEWED);
        if (ignoreArg != null && ignoreArg.trim().equals("on")) {
            IGNORENR = true;
        }

        LITERALSOURCE = createAnnoFromSource(new TreeSet<FlowPermission>(
                Arrays.asList(FlowPermission.LITERAL)));

        final Set<FlowPermission> literalSink = new TreeSet<FlowPermission>(
                flowPolicy.getSinkFromSource(FlowPermission.LITERAL, true));
        FROMLITERALSINK = createAnnoFromSink(literalSink);
        
        CONDITIONALSINK = createAnnoFromSink(new TreeSet<FlowPermission>(
                Arrays.asList(FlowPermission.CONDITIONAL)));

        final Set<FlowPermission> condtionalSource = new TreeSet<FlowPermission>(
                flowPolicy.getSourceFromSink(FlowPermission.CONDITIONAL, true));
        FROMCONDITIONALSOURCE = createAnnoFromSource(condtionalSource);

        String unfilteredStr = getOption(MSG_FILTER_OPTION);
        if (unfilteredStr == null) {
            unfilteredMessages = null;
        } else {
            final String[] unfilteredMsgs = unfilteredStr.split(":");
            unfilteredMessages = new HashSet<String>();
            for (final String unfilteredMsg : unfilteredMsgs) {
                if (!unfilteredMsg.trim().isEmpty()) {
                    unfilteredMessages.add(unfilteredMsg.trim());
                }
            }

            if (unfilteredMessages.isEmpty()) {
                unfilteredMessages = null;
            }
        }

        flowAnalizer = new FlowAnalyzer(getFlowPolicy());
    }


    public  AnnotationMirror createAnnoFromSink(final Set<FlowPermission> sinks) {
        final AnnotationBuilder builder = new AnnotationBuilder(processingEnv,
                Sink.class);
        builder.setValue("value", sinks.toArray(new FlowPermission[sinks.size()]));
        return builder.build();
    }

    public  AnnotationMirror createAnnoFromSource(Set<FlowPermission> sources) {
        final AnnotationBuilder builder = new AnnotationBuilder(processingEnv,
                Source.class);
        builder.setValue("value", sources.toArray(new FlowPermission[sources.size()]));
        return builder.build();
    }

    protected ExecutableElement sourceValue;
    protected ExecutableElement sinkValue;


    @Override
    public void typeProcessingOver() {
        printMethods();
        flowAnalizer.printImpliedFlowsVerbose();
        flowAnalizer.printImpliedFlowsForbidden();
        flowAnalizer.printAllFlows();
        super.typeProcessingOver();
    }

    // TODO: would be nice if you could pass a file name
    private final String printMissMethod = "missingAPI.astub";
    // TODO: would be nice if there was a command line argument to turn this on
    // and off
    private final boolean printFrequency = true;

    private void printMethods() {
        if (notInStubFile.isEmpty())
            return;
        PrintStream out;
        int methodCount = 0;
        try {
            out = new PrintStream(new File(printMissMethod));
        } catch (FileNotFoundException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
            return;
        }
        for (String pack : notInStubFile.keySet()) {
            out.println("package " + pack + ";");
            for (String clss : notInStubFile.get(pack).keySet()) {
                out.println("class " + clss + "{");
                Map<Element, Integer> map = notInStubFile.get(pack).get(clss);
                for (Element element : map.keySet()) {
                    StubGenerator stubGen = new StubGenerator(out);
                    if (printFrequency)
                        out.println("    //" + map.get(element) + " (" + element.getSimpleName()
                                + ")");
                    stubGen.skeletonFromMethod(element);
                    methodCount++;
                }
                out.println("}");
            }
        }
        System.err.println(methodCount + " methods to annotate.");
    }

    @Override
    protected MultiGraphQualifierHierarchy.MultiGraphFactory createQualifierHierarchyFactory() {
        return new MultiGraphQualifierHierarchy.MultiGraphFactory(this);
    }

    @Override
    public QualifierHierarchy createQualifierHierarchy(MultiGraphFactory factory) {
        return new FlowQualifierHierarchy(factory);
    }

    private class FlowQualifierHierarchy extends MultiGraphQualifierHierarchy {

        protected FlowQualifierHierarchy(MultiGraphFactory f) {
            super(f);
        }

        @Override
        protected Set<AnnotationMirror> findBottoms(
                Map<AnnotationMirror, Set<AnnotationMirror>> supertypes) {
            Set<AnnotationMirror> newbottoms = AnnotationUtils.createAnnotationSet();
            newbottoms.add(NOSOURCE);
            newbottoms.add(ANYSINK);
            return newbottoms;
        }

        @Override
        protected Set<AnnotationMirror> findTops(
                Map<AnnotationMirror, Set<AnnotationMirror>> supertypes) {
            Set<AnnotationMirror> newtops = AnnotationUtils.createAnnotationSet();
            newtops.add(ANYSOURCE);
            newtops.add(NOSINK);
            return newtops;
        }

        private boolean isSourceQualifier(AnnotationMirror anno) {
            return NOSOURCE.getAnnotationType().equals(anno.getAnnotationType())
                    || isPolySourceQualifier(anno);
        }

        private boolean isPolySourceQualifier(AnnotationMirror anno) {
            return POLYSOURCE.getAnnotationType().equals(anno.getAnnotationType());
        }

        private boolean isSinkQualifier(AnnotationMirror anno) {
            return NOSINK.getAnnotationType().equals(anno.getAnnotationType())
                    || isPolySinkQualifier(anno);
        }

        private boolean isPolySinkQualifier(AnnotationMirror anno) {
            return POLYSINK.getAnnotationType().equals(anno.getAnnotationType());
        }

        @Override
        public AnnotationMirror getTopAnnotation(AnnotationMirror start) {
            if (isSourceQualifier(start)) {
                return ANYSOURCE;
            } else if (isSinkQualifier(start)) {
                return NOSINK;
            } else if (QualifierPolymorphism.isPolyAll(start)) {
                return POLYALL;
            } else {
                errorAbort("FlowChecker: unexpected AnnotationMirror: " + start);
                return null; // dead code
            }
        }

        @Override
        public boolean isSubtype(Collection<? extends AnnotationMirror> rhs,
                Collection<? extends AnnotationMirror> lhs) {
            if (rhs.isEmpty() ^ lhs.isEmpty()) {
                // TODO: more general fix
                // This happens when casting:
                /**
                 * class TypeAsKeyHashMap<T> { public <S extends T> S get(T
                 * type) { return (S) type; } }
                 */

                // super will give this error
                // error: MultiGraphQualifierHierarchy: empty annotations in
                // lhs: or rhs:

                return false;
            }
            return super.isSubtype(rhs, lhs);
        }

        @Override
        public boolean isSubtype(AnnotationMirror rhs, AnnotationMirror lhs) {
            try {
                checkAny(rhs);
                checkAny(lhs);
            } catch (Exception e) {
                // TODO Auto-generated catch block
                System.out.println(e.getMessage());
                // e.printStackTrace();
                // System.exit(0);
            }

            if (isSourceQualifier(rhs)) {
                if (isPolySourceQualifier(lhs)) {
                    // If LHS is poly, rhs has to be bottom or poly qualifier.
                    return AnnotationUtils.areSame(rhs, NOSOURCE)
                            || AnnotationUtils.areSame(rhs, POLYSOURCE)
                            || AnnotationUtils.areSame(rhs, POLYALL);
                } else if (isPolySourceQualifier(rhs)) {
                    // If RHS is poly, lhs has to be top or poly qualifier.
                    return AnnotationUtils.areSame(lhs, ANYSOURCE)
                            || AnnotationUtils.areSame(lhs, POLYSOURCE)
                            || AnnotationUtils.areSame(lhs, POLYALL);
                } else {
                    if (!isSourceQualifier(lhs)) {
                        return false;
                    }
                    Set<FlowPermission> lhssrc = Flow.getSources(lhs);
                    Set<FlowPermission> rhssrc = Flow.getSources(rhs);
                 // TODO: Remove the ANY below when we start warning about Source(ANY, Something else)
                    return AnnotationUtils.areSame(lhs, ANYSOURCE) ||
                            lhssrc.containsAll(rhssrc) || lhssrc.contains(FlowPermission.ANY);
                }
            } else if (isSinkQualifier(rhs)) {
                if (isPolySinkQualifier(lhs)) {
                    // If LHS is poly, rhs has to be bottom or poly qualifier.
                    return AnnotationUtils.areSame(rhs, ANYSINK)
                            || AnnotationUtils.areSame(rhs, POLYSINK);
                } else if (isPolySinkQualifier(rhs)) {
                    // If RHS is poly, lhs has to be top or poly qualifier.
                    return AnnotationUtils.areSame(lhs, NOSINK)
                            || AnnotationUtils.areSame(lhs, POLYSINK);
                } else {
                    if (!isSinkQualifier(lhs)) {
                        return false;
                    }
                    Set<FlowPermission> lhssnk = Flow.getSinks(lhs);
                    Set<FlowPermission> rhssnk = Flow.getSinks(rhs);
                    return lhssnk.isEmpty() || rhssnk.containsAll(lhssnk)
                            || (rhssnk.contains(FlowPermission.ANY) && rhssnk.size() == 1);
                }
            } else if (QualifierPolymorphism.isPolyAll(rhs)) {
                // If RHS is polyall, the LHS has to be a top qualifier or also
                // poly.
                return AnnotationUtils.areSame(lhs, NOSINK)
                        || AnnotationUtils.areSame(lhs, ANYSOURCE)
                        || AnnotationUtils.areSame(lhs, POLYSINK)
                        || AnnotationUtils.areSame(lhs, POLYSOURCE);

            } else {
                errorAbort("FlowChecker: unexpected AnnotationMirrors: " + rhs + " and " + lhs);
                return false; // dead code
            }
        }

        private void checkAny(AnnotationMirror anm) throws Exception {
            boolean isPolySink = AnnotationUtils.areSame(anm, POLYSINK);
            boolean isPolySource = AnnotationUtils.areSame(anm, POLYSOURCE);

            if (!isPolySource && isSourceQualifier(anm)) {
                Set<FlowPermission> sources = Flow.getSources(anm);
                if (sources.contains(FlowPermission.ANY) && sources.size() > 1) {
                    throw new Exception("Found FlowPermission.ANY and something else");
                }
            }
            if (!isPolySink && isSinkQualifier(anm)) {
                Set<FlowPermission> sinks = Flow.getSinks(anm);
                if (sinks.contains(FlowPermission.ANY) && sinks.size() > 1) {
                    throw new Exception("Found FlowPermission.ANY and something else");
                }
            }

        }

        @Override
        protected void addPolyRelations(QualifierHierarchy qualHierarchy,
                Map<AnnotationMirror, Set<AnnotationMirror>> fullMap,
                Map<AnnotationMirror, AnnotationMirror> polyQualifiers, Set<AnnotationMirror> tops,
                Set<AnnotationMirror> bottoms) {
            AnnotationUtils.updateMappingToImmutableSet(fullMap, NOSOURCE,
                    Collections.singleton(POLYALL));
            AnnotationUtils.updateMappingToImmutableSet(fullMap, NOSOURCE,
                    Collections.singleton(POLYSOURCE));
            AnnotationUtils.updateMappingToImmutableSet(fullMap, ANYSINK,
                    Collections.singleton(POLYALL));
            AnnotationUtils.updateMappingToImmutableSet(fullMap, ANYSINK,
                    Collections.singleton(POLYSINK));
            Set<AnnotationMirror> polyallTops = AnnotationUtils.createAnnotationSet();
            polyallTops.add(ANYSOURCE);
            polyallTops.add(NOSINK);
            AnnotationUtils.updateMappingToImmutableSet(fullMap, POLYALL, polyallTops);
            AnnotationUtils.updateMappingToImmutableSet(fullMap, POLYSOURCE,
                    Collections.singleton(ANYSOURCE));
            AnnotationUtils.updateMappingToImmutableSet(fullMap, POLYSINK,
                    Collections.singleton(NOSINK));
        }

        @Override
        public AnnotationMirror leastUpperBound(AnnotationMirror a1, AnnotationMirror a2) {

            if (isSubtype(a1, a2))
                return a2;
            if (isSubtype(a2, a1))
                return a1;

            if (AnnotationUtils.areSameIgnoringValues(a1, a2)) {
                if (AnnotationUtils.areSameIgnoringValues(a1, SOURCE)) {
                    final Set<FlowPermission> superset = Flow.unionSources(a1, a2);
                    return boundSource(superset);

                } else if (AnnotationUtils.areSameIgnoringValues(a1, SINK)) {
                    final Set<FlowPermission> intersection = Flow.intersectSinks(a1, a2);
                    return boundSink(intersection);
                }
                // Poly Flows must be handled as if they are Top Type
            } else if (AnnotationUtils.areSame(a1, POLYSINK)) {
                if (AnnotationUtils.areSameIgnoringValues(a2, SINK)) {
                    return boundSink(new TreeSet<FlowPermission>());
                }

            } else if (AnnotationUtils.areSame(a2, POLYSINK)) {
                if (AnnotationUtils.areSameIgnoringValues(a1, SINK)) {
                    return boundSink(new TreeSet<FlowPermission>());
                }
            } else if (AnnotationUtils.areSame(a1, POLYSOURCE)) {
                if (AnnotationUtils.areSameIgnoringValues(a2, SOURCE)) {
                    Set<FlowPermission> top = new TreeSet<FlowPermission>();
                    top.add(FlowPermission.ANY);
                    return boundSource(top);
                }

            } else if (AnnotationUtils.areSame(a2, POLYSOURCE)) {
                if (AnnotationUtils.areSameIgnoringValues(a1, SOURCE)) {
                    Set<FlowPermission> top = new TreeSet<FlowPermission>();
                    top.add(FlowPermission.ANY);
                    return boundSource(top);
                }
            }

            return super.leastUpperBound(a1, a2);
        }

        @Override
        public AnnotationMirror greatestLowerBound(AnnotationMirror a1, AnnotationMirror a2) {

            if (AnnotationUtils.areSame(a1, a2))
                return a1;

            if (AnnotationUtils.areSameIgnoringValues(a1, a2)) {
                if (AnnotationUtils.areSameIgnoringValues(a1, SOURCE)) {
                    final Set<FlowPermission> intersection = Flow.intersectSources(a1, a2);
                    return boundSource(intersection);

                } else if (AnnotationUtils.areSameIgnoringValues(a1, SINK)) {
                    final Set<FlowPermission> superSet = Flow.unionSinks(a1, a2);
                    return boundSink(superSet);

                }
                // Poly Flows must be handled as if they are Bottom Type
            } else if (AnnotationUtils.areSame(a1, POLYSINK)) {
                if (AnnotationUtils.areSameIgnoringValues(a2, SINK)) {
                    Set<FlowPermission> bottom = new TreeSet<FlowPermission>();
                    bottom.add(FlowPermission.ANY);
                    return boundSink(bottom);
                }

            } else if (AnnotationUtils.areSame(a2, POLYSINK)) {
                if (AnnotationUtils.areSameIgnoringValues(a1, SINK)) {
                    Set<FlowPermission> bottom = new TreeSet<FlowPermission>();
                    bottom.add(FlowPermission.ANY);
                    return boundSink(bottom);
                }
            } else if (AnnotationUtils.areSame(a1, POLYSOURCE)) {
                if (AnnotationUtils.areSameIgnoringValues(a2, SOURCE)) {
                    return boundSource(new TreeSet<FlowPermission>());
                }

            } else if (AnnotationUtils.areSame(a2, POLYSOURCE)) {
                if (AnnotationUtils.areSameIgnoringValues(a1, SOURCE)) {
                    return boundSource(new TreeSet<FlowPermission>());
                }
            }
            return super.greatestLowerBound(a1, a2);
        }

        private AnnotationMirror boundSource(final Set<FlowPermission> flowSource) {

            final AnnotationMirror am;
            if (flowSource.contains(FlowPermission.ANY)) { // contains all
                                                           // Source
                am = getTopAnnotation(SOURCE);
            } else if (flowSource.isEmpty()) {
                am = getBottomAnnotation(SOURCE);
            } else {
                am = createAnnoFromSource(flowSource);
            }
            return am;
        }

        private AnnotationMirror boundSink(final Set<FlowPermission> flowSink) {
            final AnnotationMirror am;
            if (flowSink.isEmpty()) {
                am = getTopAnnotation(SINK);
            } else if (flowSink.contains(FlowPermission.ANY)) { // contains all
                                                                // Sink
                am = getBottomAnnotation(SINK);
            } else {
                am = createAnnoFromSink(flowSink);
            }
            return am;
        }
    }

    public FlowPolicy getFlowPolicy() {
        return flowPolicy;
    }

    public FlowAnalyzer getFlowAnalizer() {
        return flowAnalizer;
    }

    @Override
    protected void message(Diagnostic.Kind kind, Object source, /*@CompilerMessageKey*/
            String msgKey, Object... args) {
        if (unfilteredMessages == null || unfilteredMessages.contains(msgKey)) {
            super.message(kind, source, msgKey, args);
        }
    }
}
