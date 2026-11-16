// Adapted from the verified donor audit. Android framework members resolve on the device,
// so packaged vendor framework stubs are excluded from member verification.
import java.io.*;
import java.nio.file.*;
import java.util.*;
import com.android.tools.smali.dexlib2.*;
import com.android.tools.smali.dexlib2.iface.*;
import com.android.tools.smali.dexlib2.iface.instruction.*;
import com.android.tools.smali.dexlib2.iface.reference.*;
public class CarlinkApkAudit {
 static Map<String,ClassDef> all=new TreeMap<>();
 static Set<String> sdkTypes=new HashSet<>();
 static String sig(MethodReference m){return m.getName()+m.getParameterTypes()+m.getReturnType();}
 static boolean method(String owner,MethodReference target,Set<String> seen){
  if(owner==null||!seen.add(owner))return false;ClassDef c=all.get(owner);if(c==null)return true; // platform inheritance remains runtime-verified
  for(Method m:c.getMethods())if(sig(m).equals(sig(target)))return true;
  if(target.getName().equals("<init>"))return false;
  if(method(c.getSuperclass(),target,seen))return true;
  for(String i:c.getInterfaces())if(method(i,target,seen))return true;return false;
 }
 static boolean field(String owner,FieldReference target,Set<String> seen){
  if(owner==null||!seen.add(owner))return false;ClassDef c=all.get(owner);if(c==null)return true;
  for(Field f:c.getFields())if(f.getName().equals(target.getName())&&f.getType().equals(target.getType()))return true;
  if(field(c.getSuperclass(),target,seen))return true;for(String i:c.getInterfaces())if(field(i,target,seen))return true;return false;
 }
 public static void main(String[] a)throws Exception{
  MultiDexContainer<?> dex=DexFileFactory.loadDexContainer(new File(a[0]),Opcodes.forApi(28));List<String> errors=new ArrayList<>(),checks=new ArrayList<>();int refs=0;
  for(String entry:dex.getDexEntryNames()){int n=0;var classes=dex.getEntry(entry).getDexFile().getClasses();boolean sdk=classes.stream().anyMatch(c->c.getType().equals("Lcom/ucar/vehiclesdk/UCarAdapter;"));for(ClassDef c:classes){n++;if(sdk)sdkTypes.add(c.getType());if(all.put(c.getType(),c)!=null)errors.add("DUPLICATE "+c.getType());}checks.add(entry+" definitions="+n);}
  for(String s:List.of("Lcom/ucar/vehiclesdk/ICarConnectListener;","Lcom/ucar/vehiclesdk/ICarInitCallback;","Lcom/ucar/vehiclesdk/UCarAdapter;","Lcom/share/connect/ShareLinkService;","Lcom/shilapi/xcertplay/carlink/CarlinkBridge;","Lcom/shilapi/xcertplay/carlink/CarlinkSdkLog;")){if(!all.containsKey(s))errors.add("MISSING "+s);else checks.add("DEFINED "+s);}
  for(ClassDef c:all.values())for(Method m:c.getMethods())if(m.getImplementation()!=null)for(Instruction ins:m.getImplementation().getInstructions())if(ins instanceof ReferenceInstruction){
   Reference r=((ReferenceInstruction)ins).getReference();
   if(r instanceof MethodReference mr && sdkTypes.contains(c.getType())) {
    if((mr.getDefiningClass().equals("Landroid/util/Log;") && List.of("v","d","i","w","e","wtf","println").contains(mr.getName())) || (mr.getName().equals("printStackTrace") && mr.getParameterTypes().isEmpty()) || (mr.getDefiningClass().equals("Ljava/io/PrintStream;") && mr.getName().equals("println")))
     errors.add("UNGATED SDK LOG " + c.getType() + " -> " + mr);
   }
   if(r instanceof MethodReference){MethodReference mr=(MethodReference)r;if(!mr.getDefiningClass().startsWith("Landroid/") && all.containsKey(mr.getDefiningClass())){refs++;if(!method(mr.getDefiningClass(),mr,new HashSet<>()))errors.add("METHOD "+c.getType()+" "+sig(m)+" -> "+mr);}}
   else if(r instanceof FieldReference){FieldReference fr=(FieldReference)r;if(!fr.getDefiningClass().startsWith("Landroid/") && all.containsKey(fr.getDefiningClass())){refs++;if(!field(fr.getDefiningClass(),fr,new HashSet<>()))errors.add("FIELD "+c.getType()+" -> "+fr);}}
  }
  checks.add("Total definitions="+all.size());checks.add("Internal member references checked="+refs);checks.add("Errors="+errors.size());checks.add("Platform inherited members are not proven by this static check; runtime/privilege/native behavior requires vehicle testing.");checks.addAll(new TreeSet<>(errors));Files.write(Path.of(a[1]),checks);for(String l:checks)System.out.println(l);if(!errors.isEmpty())System.exit(1);
 }
}
