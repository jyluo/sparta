package sparta.checkers.intents;

import org.checkerframework.common.basetype.BaseTypeChecker;
import org.checkerframework.framework.type.AnnotatedTypeFactory;
import org.checkerframework.framework.type.AnnotatedTypeMirror;
import org.checkerframework.framework.util.AnnotationBuilder;

import org.checkerframework.javacutil.AnnotationUtils;
import org.checkerframework.javacutil.ElementUtils;
import org.checkerframework.javacutil.InternalUtils;
import org.checkerframework.javacutil.TreeUtils;
import org.checkerframework.javacutil.TypesUtils;

import java.lang.annotation.Annotation;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Name;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;

import sparta.checkers.Flow;
import sparta.checkers.quals.FlowPermission;
import sparta.checkers.quals.GetExtra;
import sparta.checkers.quals.ParameterizedFlowPermission;
import sparta.checkers.quals.Extra;
import sparta.checkers.quals.IntentMap;
import sparta.checkers.quals.PutExtra;
import sparta.checkers.quals.ReceiveIntent;
import sparta.checkers.quals.SendIntent;
import sparta.checkers.quals.SetIntentFilter;

import com.sun.source.tree.ClassTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.ModifiersTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.Tree.Kind;
import com.sun.source.tree.TypeParameterTree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.TreePath;
import com.sun.tools.javac.code.Symbol.ClassSymbol;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.code.Types;

public class IntentUtils {
    
    /**
     * Method that receives an @IntentMap and a <code> key </code>
     * and return the @Extra with that key and <code>null</code> if it 
     * does not contain the key.
     */
    public static AnnotationMirror getIExtra(AnnotationMirror intentExtras, 
            String key) {
        List<AnnotationMirror> iExtrasList = getIExtras(intentExtras);
        for(AnnotationMirror iExtra : iExtrasList) {
            if(key.equals(getKeyName(iExtra))) {
                return iExtra;
            }
        }
        return null;
    }
    
    public static List<AnnotationMirror> getIExtras(AnnotationMirror intentExtra) {
        return AnnotationUtils
            .getElementValueArray(intentExtra, "value", AnnotationMirror.class, true);
    }
    

    public static AnnotationMirror getIExtra(AnnotatedTypeMirror intentMapAnno, String keyName) {
        if (intentMapAnno.hasAnnotation(IntentMap.class)) {
            return getIExtra(
                    intentMapAnno.getAnnotation(IntentMap.class), keyName);
        }
        return null;
    }

    /**
     * Return true if @IntentMap has this key
     */

    public static boolean hasKey(AnnotationMirror intentExtras, String key) {
        List<AnnotationMirror> iExtrasList = getIExtras(intentExtras);
        for(AnnotationMirror iExtra : iExtrasList) {
            String iExtraKey = getKeyName(iExtra);
            if(iExtraKey.equals(key)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Return the union of sources from 2 @Extra annotations
     */

    public static Set<ParameterizedFlowPermission> unionSourcesIExtras(AnnotationMirror iExtra1, 
            AnnotationMirror iExtra2) {
        return  Flow.unionSources(getSourcesPFP(iExtra1), getSourcesPFP(iExtra2));

    }

    public static Set<ParameterizedFlowPermission> getSourcesPFP(AnnotationMirror iExtra) {
        return Flow.convertToParameterizedFlowPermission(getSources(iExtra));
    }
    
    public static Set<FlowPermission> getSources(AnnotationMirror iExtra) {
        return new TreeSet<FlowPermission>(
            AnnotationUtils.getElementValueEnumArray(
                iExtra, "source", FlowPermission.class, true));
    }

    /**
     * Return the union of sinks from 2 @Extra annotations
     */

    public static Set<ParameterizedFlowPermission> unionSinksIExtras(AnnotationMirror iExtra1, 
            AnnotationMirror iExtra2) {
        return Flow.unionSinks(getSinksPFP(iExtra1), getSinksPFP(iExtra2));
    }

    public static Set<ParameterizedFlowPermission> getSinksPFP(AnnotationMirror iExtra) {
        return Flow.convertToParameterizedFlowPermission(getSinks(iExtra));
    }
    
    public static Set<FlowPermission> getSinks(AnnotationMirror iExtra) {
        return new TreeSet<FlowPermission>(
            AnnotationUtils.getElementValueEnumArray(
                iExtra, "sink", FlowPermission.class, true));
    }




    /**
     * Return the intersection of sources from 2 @Extra annotations
     */

    public static Set<ParameterizedFlowPermission> intersectionSourcesIExtras(AnnotationMirror iExtra1, 
            AnnotationMirror iExtra2) {
        return Flow.intersectSinks(getSourcesPFP(iExtra1), getSourcesPFP(iExtra2));
    }

    /**
     * Return the intersection of sinks from 2 @Extra annotations
     */

    public static Set<ParameterizedFlowPermission> intersectionSinksIExtras(AnnotationMirror iExtra1, 
            AnnotationMirror iExtra2) {
        return Flow.intersectSinks(getSinksPFP(iExtra1),  getSinksPFP(iExtra2));

    }

    /**
     * Creates a new IExtra AnnotationMirror
     * @param key
     * @param sources
     * @param sinks
     * @param processingEnv
     * @return
     */

    public static AnnotationMirror createIExtraAnno(String key,
            AnnotationMirror sources, AnnotationMirror sinks, 
            ProcessingEnvironment processingEnv) {
        final AnnotationBuilder builder = new AnnotationBuilder(processingEnv,
            Extra.class);
        Set<FlowPermission> sourcesSet = Flow.convertFromParameterizedFlowPermission(Flow.getSources(sources));
        Set<FlowPermission> sinksSet = Flow.convertFromParameterizedFlowPermission(Flow.getSinks(sinks));
        
        builder.setValue("key", key);
        builder.setValue("source",
            sourcesSet.toArray(new FlowPermission[sourcesSet.size()]));
        builder.setValue("sink",
            sinksSet.toArray(new FlowPermission[sinksSet.size()]));
        return builder.build();
    }

    /**
     * Returns a new @IntentMap containing all @Extra from <code>intentExtras</code>
     * and a new <code>IExtra</code>.
     * @param intentExtras
     * @param iExtra
     * @param processingEnv
     * @return
     */

    public static AnnotationMirror addIExtraToIntentExtras(
            AnnotationMirror intentExtras, AnnotationMirror iExtra, 
            ProcessingEnvironment processingEnv) {
        final AnnotationBuilder builder = new AnnotationBuilder(processingEnv,
            IntentMap.class);
        List<AnnotationMirror> iExtrasList = getIExtras(intentExtras);
        iExtrasList.add(iExtra);
        builder.setValue("value", iExtrasList.toArray());
        return builder.build();
    }

    /**
     * Returns a new @IntentMap containing all @Extra from <code>intentExtras</code>
     * and all <code>IExtras</code>. 
     * @param intentExtras
     * @param iExtras
     * @param processingEnv
     * @return
     */

    public static AnnotationMirror addIExtrasToIntentExtras(AnnotationMirror intentExtras, 
            List<AnnotationMirror> iExtras, ProcessingEnvironment processingEnv) {
        AnnotationMirror result = intentExtras;
        for (AnnotationMirror iExtra : iExtras) {
            result = addIExtraToIntentExtras(result, iExtra, processingEnv);
        }
        return result;
    }

    /**
     * Returns true if the MethodInvocationTree corresponds to one of the <code>Intent.getExtra()</code> calls
     * @param tree
     * @return
     */

    public static boolean isGetExtra(MethodInvocationTree tree, AnnotatedTypeFactory atypeFactory) {
        Element ele = InternalUtils.symbol(tree);
        return atypeFactory.getDeclAnnotation(ele, GetExtra.class) != null;
    }

    /**
     * Returns true if the MethodInvocationTree corresponds to one of the <code>Intent.putExtra()</code> calls
     * @param tree
     * @return
     */

    public static boolean isPutExtra(MethodInvocationTree tree, AnnotatedTypeFactory atypeFactory) {
        Element ele = InternalUtils.symbol(tree);
        return atypeFactory.getDeclAnnotation(ele, PutExtra.class) != null;
    }
    
    /**
     * Returns true if the MethodInvocationTree corresponds to one of the <code>sendIntent()</code> calls:
     * E.g.: startActivity(); startService(); sendBroadcast();
     * @param tree
     * @return
     */

    public static boolean isSendIntent(MethodInvocationTree tree, AnnotatedTypeFactory atypeFactory) {
        Element ele = InternalUtils.symbol(tree);
        return atypeFactory.getDeclAnnotation(ele, SendIntent.class) != null;
    }
    
    /**
     * Returns the ExecutableElement of the getIntent() method declaration
     * for the class passed as parameter.
     *
     */
    public static ExecutableElement getMethodGetIntent(BaseTypeChecker checker, String canonicalClassName) {
        TypeElement mapElt = checker.getProcessingEnvironment().getElementUtils().getTypeElement(canonicalClassName);
        ExecutableElement getIntentMethod = null;
        for (ExecutableElement exec : ElementFilter.methodsIn(mapElt.getEnclosedElements())) {
            if (exec.getSimpleName().contentEquals("getIntent")
                    && exec.getParameters().size() == 0)
                getIntentMethod = exec;
        }
        
        return getIntentMethod; 
    }
    
    /**
     * Returns the ExecutableElement of the setIntent(intent) method declaration
     * for the class passed as parameter.
     *
     */
    public static ExecutableElement getMethodSetIntent(BaseTypeChecker checker, String canonicalClassName) {
        TypeElement mapElt = checker.getProcessingEnvironment().getElementUtils().getTypeElement(canonicalClassName);
        ExecutableElement setIntentMethod = null;
        for (ExecutableElement exec : ElementFilter.methodsIn(mapElt.getEnclosedElements())) {
            if (exec.getSimpleName().contentEquals("setIntent")
                    && exec.getParameters().size() == 1)
                setIntentMethod = exec;
        }
        
        return setIntentMethod; 
    }
    
    /**
     * Returns true if the MethodInvocationTree corresponds to one of the <code>receiveIntent()</code> calls:
     * E.g.: onBind(); onReceive(); getIntent();
     * @param tree
     * @return
     */
    
    public static boolean isReceiveIntent(MethodInvocationTree tree, AnnotatedTypeFactory atypeFactory) {
        Element ele = InternalUtils.symbol(tree);
        return atypeFactory.getDeclAnnotation(ele, ReceiveIntent.class) != null;
    }
    
    /**
     * Returns true if the MethodInvocationTree corresponds to one of the methods that sets an intent filter
     * to an intent:
     * E.g.: setAction(); addCategory(); setData();
     * @param tree
     * @return
     */

    public static boolean isSetIntentFilter(MethodInvocationTree tree, AnnotatedTypeFactory atypeFactory) {
        Element ele = InternalUtils.symbol(tree);
        return atypeFactory.getDeclAnnotation(ele, SetIntentFilter.class) != null;
    }

    
    
    public static String retrieveSendIntentPath(TreePath treePath) {
      String senderString = "";
      //senderString = package.class
      ClassTree classTree = TreeUtils.enclosingClass(treePath);  
      ClassSymbol ele = (ClassSymbol) InternalUtils.symbol(classTree);
      senderString = ele.flatname.toString();
      
      //senderString += .method(
      MethodTree methodTree = TreeUtils.enclosingMethod(treePath);
      senderString += "." + methodTree.getName() + "(";
      
      //senderString += args)
      //Removing annotation types from parameters
      List<? extends VariableTree> args = methodTree.getParameters();
      for(VariableTree arg : args) {
          Type type = (Type) InternalUtils.typeOf(arg.getType());       
          String typeStringFormat = type.annotatedType(Type.noAnnotations).toString();
          String typeNoAnnotations = typeStringFormat;
          //Handling arrays
          if(typeStringFormat.contains(" ")) {
              String[] typeWithAnnotations = typeStringFormat.split(" ");
              typeNoAnnotations = typeWithAnnotations[typeWithAnnotations.length-1];
          }
          senderString += typeNoAnnotations + ",";
          
      }
      //Removing last comma in case of args.size() != 0.
      if(args.size() > 0) {
          senderString = senderString.substring(0,senderString.length()-1);
      }
      senderString += ')';
      
      //Component map does not have entries with Generics parameters
      //due to epicc's limitations. Need to remove <?> from parameters.
      senderString = senderString.replaceAll("<([^;]*)>", "");
      
      return senderString;
    }
    
    public  AnnotationMirror createAnnoFromValues(ProcessingEnvironment processingEnv, 
        Class<? extends Annotation> anno, 
        List<CharSequence> elementsNames, List<Enum<?>> values) {
        final AnnotationBuilder builder = new AnnotationBuilder(processingEnv, anno);
        if(elementsNames.size() != values.size()) {
            //Both need to have the same amount of elements.
            throw new RuntimeException();
        }
        for(int i = 0; i < elementsNames.size(); i++) {
            builder.setValue(elementsNames.get(i), values.get(i));
        }
        return builder.build();
    }
    
    public static String getKeyName(AnnotationMirror iExtra) {
        return AnnotationUtils.getElementValue(iExtra, "key",
            String.class, true);
    }

}
