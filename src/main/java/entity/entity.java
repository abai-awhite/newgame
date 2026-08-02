package entity;

import java.awt.*;
import java.awt.image.BufferedImage;

/**
 * 实体基类，定义所有游戏实体（如玩家、敌人 NPC）共有的属性和方法。
 *
 * <h3>设计目的</h3>
 * <p>作为所有实体类的父类，提供公共的状态字段（位置、速度、方向、动画），避免子类重复定义。
 * 子类（如 Player）通过继承获得这些字段，并重写或扩展行为。</p>
 *
 * <h3>字段说明</h3>
 * <ul>
 *   <li><b>位置</b>：x, y 为实体的左上角世界坐标（像素单位）</li>
 *   <li><b>速度</b>：speed 为每逻辑帧移动的像素数</li>
 *   <li><b>方向</b>：direction 字符串标识实体的朝向（up/down/left/right/null）</li>
 *   <li><b>动画</b>：counter 与 incrementer 控制站立动画的帧循环</li>
 *   <li><b>图片</b>：各方向图片数组，null 表示站立帧序列</li>
 * </ul>
 *
 * <h3>子类扩展提示</h3>
 * <p>子类应重写 paintComponent(Graphics2D) 方法以实现自定义渲染。
 * 当前 paintComponent 实现被注释掉，仅保留图片切换逻辑供参考。</p>
 *
 * @see Player
 */
public class entity {

    /**
     * 实体在世界中的 X 坐标（像素，左上角为基准点）。
     * 注意：对于玩家，该字段已被 currentX/renderX 替代，用于支持插值渲染。
     */
    public double x;

    /**
     * 实体在世界中的 Y 坐标（像素，左上角为基准点）。
     * 注意：对于玩家，该字段已被 currentY/renderY 替代。
     */
    public double y;

    /**
     * 每逻辑帧移动的像素数（仅用于横向移动，纵向使用 velocityY 重力系统）。
     */
    public double speed;

    /**
     * 站立动画帧图片（循环播放：none1 → none2 → none3 → none2 → none1 ...）。
     * none4 未使用，仅作占位。
     */
    public BufferedImage none1, none2, none3, none4;

    /**
     * 向下看时的图片（未使用，当前游戏无俯视视角）。
     */
    public BufferedImage down1, down2, down3, down4;

    /**
     * 向上看时的图片（跳起时显示）。
     */
    public BufferedImage up1, up2, up3, up4;

    /**
     * 向右移动时的图片序列（当前仅使用第一帧）。
     */
    public BufferedImage right1, right2, right3, right4;

    /**
     * 向左移动时的图片序列（当前仅使用第一帧）。
     */
    public BufferedImage left1, left2, left3, left4;

    /**
     * 实体当前朝向。
     * 有效值：up / down / right / left / null（静止）
     */
    public String direction;

    /**
     * 动画帧计数器累加器，每逻辑帧递增，控制动画播放速度。
     */
    public int incrementer = 0;

    /**
     * 当前播放的动画帧索引（1~4）。
     * 用于 none1~none3 的循环切换。
     */
    public int counter = 1;

//   /**
//    * 实体渲染方法（已注释，仅作参考）。
//    * 子类应重写此方法实现自定义渲染逻辑。
//    *
//    * @param g2 图形上下文
//    */
//   public  void  paintComponent(Graphics2D g2) {
//      BufferedImage image = null;
//      switch (direction) {
//         case "up":
//            image = up1;
//            break;
//         case "down":
//            image = down1;
//            break;
//         case "right":
//            image = right1;
//            break;
//         case "left":
//            image = left1;
//            break;
//         case "null":
//            if(counter==1){image = none1;}
//            if (counter==2){image = none2;}
//            if(counter==3){image = none3;}
//            if (counter==4){image = none2;}
//            break;
//         default:
//            throw new IllegalStateException("Unexpected value: " + direction);
//      }
////        g2.drawImage(image , (int) x,(int) y, Gamepanel.titlesize, Gamepanel.titlesize , null);
//   }
}