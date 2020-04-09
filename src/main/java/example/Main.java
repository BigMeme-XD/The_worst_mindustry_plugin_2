package example;

import arc.Events;
import arc.util.CommandHandler;
import arc.util.Log;
import mindustry.content.Blocks;
import mindustry.entities.type.Player;
import mindustry.entities.type.base.BuilderDrone;
import mindustry.game.EventType;
import mindustry.game.EventType.*;
import mindustry.game.Teams;
import mindustry.gen.Call;
import mindustry.plugin.Plugin;
import mindustry.type.Item;
import mindustry.type.ItemType;


import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;

import mindustry.world.Block;
import mindustry.world.blocks.storage.CoreBlock;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import static java.lang.Math.pow;
import static java.lang.Math.sqrt;
import static mindustry.Vars.*;

public class Main extends Plugin{
    static final String ALL="all";
    static final String saveFile="save.json";
    static final String directory="config/mods/The_Worst/";
    static final String prefix="[scarlet][Server][]";

    static final String[] itemIcons={"\uF838","\uF837","\uF836","\uF835","\uF832","\uF831","\uF82F","\uF82E","\uF82D","\uF82C"};
    static final HashMap<String , LoadSave> saveConfigReq=new HashMap<>();

    static int transportTime=30;

    static ArrayList<Item> items=new ArrayList<>();
    ArrayList<Interruptible> interruptibles=new ArrayList<>();

    Loadout loadout;
    Factory factory;
    CoreBuilder builder;
    Vote vote;


    public Main(){

        Events.on(PlayerChatEvent.class,e->{
            if(vote.voting && e.message.equals("y")){
                vote.addVote(e.player,1);
            }
                });

        Events.on(WorldLoadEvent.class,e-> interruptibles.forEach(Interruptible::interrupt));

        Events.on(EventType.BuildSelectEvent.class, e->{

            if(factory.requests.size()>0) {
                boolean canPlace=true;
                for(Request r:factory.requests){
                    double dist=sqrt((pow(e.tile.x-(float)(r.aPackage.x/8),2)+
                            pow(e.tile.y-(float)(r.aPackage.y/8),2)));
                    if (dist<5){
                        canPlace=false;
                        break;
                    }
                }
                if(!canPlace){
                    e.tile.removeNet();
                    if(e.builder instanceof BuilderDrone){
                        ((BuilderDrone)e.builder).kill();
                        Call.sendMessage(prefix+"Builder Drone wos destroyed after it attempt to build on drop point");
                    }else if(e.builder instanceof Player){
                        ((Player)e.builder).sendMessage(prefix+"You cannot build on unit drop point.");
                    }
                }
            }
        });

        Events.on(ServerLoadEvent.class,e->{
            load_items();
            loadout=new Loadout();
            factory=new Factory(loadout);
            builder=new CoreBuilder();
            vote=new Vote();
            interruptibles.add(loadout);
            interruptibles.add(factory);
            interruptibles.add(vote);
            saveConfigReq.put("loadout",loadout);
            saveConfigReq.put("factory",factory);
            if(!makeDir()){
                Log.info("Unable to create directory "+directory+".");
            }
            load();
        });

    }

    public void load(){
        String path=directory+saveFile;
        try(FileReader fileReader = new FileReader(path)) {
            JSONParser jsonParser=new JSONParser();
            Object obj=jsonParser.parse(fileReader);
            JSONObject saveData=(JSONObject)obj;
            for(String r:saveConfigReq.keySet()){
                if(!saveData.containsKey(r)){
                    Log.info("Failed to load save file.");
                    return;
                }
            }
            saveConfigReq.keySet().forEach((k)->saveConfigReq.get(k).load((JSONObject) saveData.get(k)));
            fileReader.close();
            Log.info("Data loaded.");
        }catch (FileNotFoundException ex) {
            Log.info("No saves found.New save file " + path + " will be created.");
            save();
        }catch (ParseException ex){
            Log.info("Json file is invalid.");
        }catch (IOException ex){
            Log.info("Error when loading data from "+path+".");
        }
    }

    public void save(){
        JSONObject saveData=new JSONObject();
        saveConfigReq.keySet().forEach((k)->saveData.put(k,saveConfigReq.get(k).save()));
        try(FileWriter file = new FileWriter(directory+saveFile))
        {
            file.write(saveData.toJSONString());
            file.close();
            Log.info("Data saved.");
        }catch (IOException ex){
            Log.info("Error when saving data.");
        }
    }

    public static boolean isNotInteger(String str){
        if(str == null || str.trim().isEmpty()) {
            return true;
        }
        for (int i = 0; i < str.length(); i++) {
            if(!Character.isDigit(str.charAt(i))) {
                return true;
            }
        }
        return false;
    }

    public static String report(String object,int amount){
        return "[orange]" + (object.equals(Main.ALL) ? Main.ALL:amount+" "+object) +"[]";
    }

    public static String timeToString(int time){
        return time/60+"sec"+time%60+"sec";
    }

    private boolean makeDir(){
        File dir=new File(directory);
        if(!dir.exists()){
            return dir.mkdir();
        }
        return true;
    }

    public static void loadingError(String address){
        Log.err(address+" has invalid type of value.It has to be integer." +
                "Property will be set to default value.");
    }

    public static void missingPropertyError(String address){
        Log.err(address+" is missing.Property will be set to default value.");
    }

    public static Integer getInt(Object integer){
        if(integer instanceof Integer){
            return (int)integer;
        }if( integer instanceof Long){
            return ((Long)integer).intValue();
        }
        return null;
    }

    public static Player findPlayer(String name){
        for(Player p:playerGroup){
            if(p.name.equals(name)){
                return p;
            }
        }
        return null;
    }

    private void load_items(){
        for(Item i:content.items()){
            if(i.type==ItemType.material){
                items.add(i);
            }
        }
    }

    public static int getStorageSize(Player player){
        int size=0;
       for(CoreBlock.CoreEntity c:player.getTeam().cores()){
           size+=c.block.itemCapacity;
       }
       return size;
    }

    public void build_core(int cost, Player player, Block core_type){
        boolean can_build=true;
        Teams.TeamData teamData = state.teams.get(player.getTeam());
        CoreBlock.CoreEntity core = teamData.cores.first();
        for(Item item:items){
            if (!core.items.has(item, cost)) {
                can_build=false;
                player.sendMessage("[scarlet]" + item.name + ":" + core.items.get(item) +"/"+ cost);
            }
        }
        if(can_build) {
            Call.onConstructFinish(world.tile(player.tileX(), player.tileY()), core_type, 0, (byte) 0, player.getTeam(), false);
            if (world.tile(player.tileX(), player.tileY()).block() == core_type) {
                player.sendMessage("[green]Core spawned!");
                Call.sendMessage("[scarlet][Server][]Player [green]"+player.name+" []has taken a portion of resources to build a core!");
                for(Item item:items){
                    core.items.remove(item, cost);
                }
            } else {
                player.sendMessage("[scarlet][Server]Core spawn failed!Invalid placement!");
            }
            return;
        }
        player.sendMessage("[scarlet][Server]Core spawn failed!Not enough resorces.");
    }
    
    @Override
    public void registerServerCommands(CommandHandler handler){
        handler.register("w-load","Reloads theWorst saved data.",arg-> load());
        handler.register("w-save","Saves theWorst data.",arg-> save());
        handler.register("w-apply-config","Applies the factory configuration.",arg->{
            factory.config();
            Log.info("Config applied.");
        });
        handler.register("w-trans-time","<value>","Sets transport time.",arg->{
            if(isNotInteger(arg[0])){
                Log.info(arg[0]+" is not an integer.");
                return;
            }
            transportTime=Integer.parseInt(arg[0]);
                });
        handler.register("w","<target> <property> <value>","Sets property of target to value/integer.",arg->{
            if(!saveConfigReq.containsKey(arg[0])){
                Log.info("Invalid target.Valid targets:"+saveConfigReq.keySet().toString());
                return;
            }
            HashMap<String ,Integer> config=saveConfigReq.get(arg[0]).get_config();
            if(!config.containsKey(arg[1])){
                Log.info(arg[0]+" has no property "+arg[1]+". Valid properties:"+config.keySet().toString());
                return;
            }
            if(isNotInteger(arg[2])){
                Log.info(arg[2]+" is not an integer.");
                return;
            }
            config.put(arg[1],Integer.parseInt(arg[2]));
            Log.info("Property changed.");
        });
    }

    @Override
    public void registerClientCommands(CommandHandler handler){
        handler.<Player>register("l-info","Shows how mani resource you have stored in the loadout and " +
                        "traveling progress.",(arg, player) -> Call.onInfoMessage(player.con,loadout.info()));

        handler.<Player>register("f-info","Shows how mani units you have in a hangar and building or " +
                "traveling progres.",(arg, player) -> Call.onInfoMessage(player.con,factory.info()));

        handler.<Player>register("l","<fill/use> <itemName/all> <itemAmount>","."
                ,(arg, player) -> {
            boolean use=arg[0].equals("use");
            Package p=loadout.verify(player,arg[1],arg[2],use);
            if (p==null){
                return;
            }
            String where=use ? "core":"loadout";
            vote.aVote(loadout, p,"launch "+report(p.object,p.amount)+" to "+where+".",
                    "launch to "+where);
        });

        handler.<Player>register("f","<build/send> <unitName/all> [unitAmount]","."
                ,(arg, player) -> {
            boolean send=arg[0].equals("send");
            Package p=factory.verify(player,arg[1],arg.length==3 ? arg[2]:"1" ,send);
            if (p==null){
                return;
            }
            String what=send ? "send":"build";
            vote.aVote(factory, p,what+" "+report(p.object,p.amount)+".", what);
        });
        handler.<Player>register("f-price","<unitName> [unitAmount]",
                "Shows price of given amount of units.",(arg,player)->{
            int amount=arg.length==1 || isNotInteger(arg[1]) ? 1:Integer.parseInt(arg[1]);
            Call.onInfoMessage(player.con,factory.price(player,arg[0],amount));
        });

        handler.<Player>register("build-core","<small/normal/big>", "Makes new core", (arg, player) -> {
            Package p=builder.verify(player,arg[0],"0" ,true);
            if (p==null){
                return;
            }
            vote.aVote(builder, p,"building "+arg[0]+" core.","core build");
        });
    }
}
class CoreBuilder implements Requester{
    @Override
    public ArrayList<Request> getRequests() {
        return null;
    }

    @Override
    public void fail(String object, int amount) {

    }

    @Override
    public String getProgress(Request request) {
        return null;
    }

    @Override
    public void launch(Package p) {
        Block to_build = Blocks.coreShard;
        switch(p.object){
            case "normal":
                to_build = Blocks.coreFoundation;

                break;
            case "big":
                to_build = Blocks.coreNucleus;
                break;
        }
        build_core(p.amount,p.target,to_build,p.x,p.y);
    }

    @Override
    public Package verify(Player player, String object, String sAmount, boolean toBase) {
        if(!object.equals("big") && !object.equals("normal") && !object.equals("small")){
            player.sendMessage(Main.prefix+"Invalid argument.");
            return null;
        }

        int storage=Main.getStorageSize(player);
        int cost=(int)(storage*.20f);
        switch(object){
            case "normal":
                cost=(int)(storage*.35f);
                break;
            case "big":
                cost=(int)(storage*.50f);
                break;
        }
        boolean can_build=true;
        CoreBlock.CoreEntity core=Loadout.getCore(player);
        for(Item item:Main.items){
            if (!core.items.has(item, cost)) {
                can_build=false;
                player.sendMessage("[scarlet]" + item.name + ":" + core.items.get(item) +"/"+ cost);
            }
        }
        if(!can_build){
            return null;
        }
        return new Package(object,cost,toBase,player,player.tileX(),player.tileY());
    }

    public void build_core(int cost, Player player, Block core_type ,int x,int y){
        CoreBlock.CoreEntity core = Loadout.getCore(player);
        Call.onConstructFinish(world.tile(x,y), core_type, 0, (byte) 0, player.getTeam(), false);
        if (world.tile(player.tileX(), player.tileY()).block() == core_type) {
            Call.sendMessage(Main.prefix+"Player [green]"+player.name+" []has taken a portion of resources to build a core!");
            for(Item item:Main.items){
                core.items.remove(item, cost);
            }
        } else {
            player.sendMessage(Main.prefix+"Core spawn failed!Invalid placement!");
        }
    }
}
