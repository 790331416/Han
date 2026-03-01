<template>
  <div class="icon-select">
    <el-popover placement="bottom-start" :width="540" trigger="click" :visible="popoverVisible">
      <template #reference>
        <el-input
          :model-value="modelValue"
          placeholder="点击选择图标"
          readonly
          @click="popoverVisible = !popoverVisible"
        >
          <template #prefix>
            <el-icon v-if="modelValue && modelValue !== '#'" style="font-size: 16px">
              <component :is="modelValue" />
            </el-icon>
          </template>
          <template #suffix>
            <el-icon v-if="modelValue" style="cursor: pointer" @click.stop="handleClear">
              <CircleClose />
            </el-icon>
          </template>
        </el-input>
      </template>
      <div class="icon-select-popper">
        <el-input v-model="searchText" placeholder="搜索图标" clearable style="margin-bottom: 10px" />
        <el-scrollbar height="280px">
          <div class="icon-grid">
            <div
              v-for="icon in filteredIcons"
              :key="icon"
              class="icon-item"
              :class="{ 'is-active': modelValue === icon }"
              @click="handleSelect(icon)"
              :title="icon"
            >
              <el-icon :size="20"><component :is="icon" /></el-icon>
              <span class="icon-name">{{ getIconLabel(icon) }}</span>
            </div>
          </div>
        </el-scrollbar>
      </div>
    </el-popover>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { CircleClose } from '@element-plus/icons-vue'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'

defineProps<{
  modelValue?: string
}>()

const emit = defineEmits<{
  'update:modelValue': [value: string]
}>()

const popoverVisible = ref(false)
const searchText = ref('')

const iconNameMap: Record<string, string> = {
  AddLocation: '添加地址', Aim: '瞄准', AlarmClock: '闹钟', Apple: '苹果', ArrowDown: '向下', ArrowDownBold: '向下粗', ArrowLeft: '向左', ArrowLeftBold: '向左粗', ArrowRight: '向右', ArrowRightBold: '向右粗', ArrowUp: '向上', ArrowUpBold: '向上粗',
  Avatar: '头像', Back: '返回', Baseball: '棒球', Basketball: '篮球', Bell: '铃铛', BellFilled: '铃铛实心', Bicycle: '自行车', Bottom: '底部', BottomLeft: '左下', BottomRight: '右下', Bowl: '碗', Box: '盒子',
  Briefcase: '公文包', Brush: '画笔', BrushFilled: '画笔实心', Burger: '汉堡', Calendar: '日历', Camera: '相机', CameraFilled: '相机实心', CaretBottom: '三角下', CaretLeft: '三角左', CaretRight: '三角右', CaretTop: '三角上',
  ChatDotRound: '聊天圆', ChatDotSquare: '聊天方', ChatLineRound: '消息圆', ChatLineSquare: '消息方', ChatRound: '对话圆', ChatSquare: '对话方', Check: '对勾', Checked: '已勾选', Cherry: '樱桃', Chicken: '鸡', CircleCheck: '圆形对勾', CircleCheckFilled: '圆形对勾实心',
  CircleClose: '圆形关闭', CircleCloseFilled: '圆形关闭实心', CirclePlus: '圆形加', CirclePlusFilled: '圆形加实心', Clock: '时钟', Close: '关闭', CloseBold: '关闭粗', CloudDownload: '云下载', CloudUpload: '云上传', Coffee: '咖啡', CoffeeCup: '咖啡杯', Coin: '硬币',
  ColdDrink: '冷饮', Collection: '收藏', CollectionTag: '收藏标签', Comment: '评论', Compass: '指南针', Connection: '连接', Coordinate: '坐标', CopyDocument: '复制文档', Cpu: '处理器', CreditCard: '信用卡', Crop: '裁剪',
  DArrowLeft: '双箭头左', DArrowRight: '双箭头右', DCaret: '双三角', DataAnalysis: '数据分析', DataBoard: '数据看板', DataLine: '数据折线', Delete: '删除', DeleteFilled: '删除实心', DeleteLocation: '删除地址', Dessert: '甜点',
  Discount: '折扣', Dish: '盘子', DishDot: '盘子点', Document: '文档', DocumentAdd: '添加文档', DocumentChecked: '已审文档', DocumentCopy: '复制文档', DocumentDelete: '删除文档', DocumentRemove: '移除文档', Download: '下载', Drizzle: '毛毛雨',
  Edit: '编辑', EditPen: '编辑笔', Eleme: '饥了么', ElemeFilled: '饥了么实心', ElementPlus: 'ElementPlus', Expand: '展开', Failed: '失败', Female: '女', Files: '文件', Film: '胶卷', Filter: '筛选', Flag: '旗帜',
  Fold: '折叠', Folder: '文件夹', FolderAdd: '新建文件夹', FolderChecked: '已审文件夹', FolderDelete: '删除文件夹', FolderOpened: '打开文件夹', FolderRemove: '移除文件夹', Food: '食物', Football: '足球', ForkSpoon: '刀叉', Fries: '薯条', FullScreen: '全屏',
  Goblet: '高脚杯', GobletFull: '满杯', GobletSquare: '方杯', GobletSquareFull: '方杯满', GoldMedal: '金牌', Goods: '商品', GoodsFilled: '商品实心', Grape: '葡萄', Grid: '网格', Guide: '引导', Handbag: '手提包',
  Headset: '耳机', Help: '帮助', HelpFilled: '帮助实心', Hide: '隐藏', Histogram: '柱状图', HomeFilled: '首页实心', HotWater: '热水', House: '房屋', IceCream: '冰淇淋', IceCreamRound: '冰淇淋圆', IceCreamSquare: '冰淇淋方', IceDrink: '冰饮', IceTea: '冰茶',
  InfoFilled: '信息实心', Iphone: '手机', Key: '钥匙', KnifeFork: '刀叉', Lightning: '闪电', Link: '链接', List: '列表', Loading: '加载', Location: '地址', LocationFilled: '地址实心', LocationInformation: '地址信息', Lock: '锁',
  Lollipop: '棒棒糖', MagicStick: '魔法棒', Magnet: '磁铁', Male: '男', Management: '管理', MapLocation: '地图地址', Medal: '奖章', Memo: '备忘录', Menu: '菜单', Message: '消息', MessageBox: '消息箱', Mic: '麦克风',
  Microphone: '话筒', MilkTea: '奶茶', Minus: '减', Money: '钱', Monitor: '显示器', Moon: '月亮', MoonNight: '夜晚', More: '更多', MoreFilled: '更多实心', MostlyCloudy: '多云', Mug: '马克杯', Mute: '静音', MuteNotification: '静音通知',
  NoSmoking: '禁止吸烟', Notebook: '笔记本', Notification: '通知', Odometer: '里程表', OfficeBuilding: '办公楼', Open: '打开', Operation: '操作', Opportunity: '机会', Orange: '橙子', Paperclip: '回形针', PartlyCloudy: '多云',
  Pear: '梨', Phone: '电话', PhoneFilled: '电话实心', Picture: '图片', PictureFilled: '图片实心', PictureRounded: '图片圆角', PieChart: '饼图', Place: '地点', Platform: '平台', Plus: '加', Pointer: '指针', Position: '位置',
  Postcard: '明信片', Pouring: '倒水', Present: '礼物', PriceTag: '价格标签', Printer: '打印机', Promotion: '推广', QuestionFilled: '问题实心', Rank: '排名', Reading: '阅读', ReadingLamp: '台灯', Refresh: '刷新', RefreshLeft: '刷新左', RefreshRight: '刷新右',
  Refrigerator: '冰箱', Remove: '移除', RemoveFilled: '移除实心', Right: '右', ScaleToOriginal: '原始尺寸', School: '学校', Scissor: '剪刀', Search: '搜索', Select: '选择', Sell: '卖出', SemiSelect: '半选', Service: '客服',
  Setting: '设置', SetUp: '配置', Share: '分享', Ship: '船', Shop: '商店', ShoppingBag: '购物袋', ShoppingCart: '购物车', ShoppingCartFull: '购物车满', ShoppingTrolley: '购物推车', Smoking: '吸烟', Soccer: '足球', SoldOut: '售罄',
  Sort: '排序', SortDown: '降序', SortUp: '升序', Stamp: '印章', Star: '星标', StarFilled: '星标实心', Stopwatch: '秒表', SuccessFilled: '成功实心', Sugar: '糖', Suitcase: '行李箱', SuitcaseLine: '行李箱线', Sunny: '晴天',
  Sunrise: '日出', Sunset: '日落', Switch: '开关', SwitchButton: '开关按钮', SwitchFilled: '开关实心', TakeawayBox: '外卖盒', Ticket: '票', Tickets: '票据', Timer: '计时器', TitleBar: '标题栏', Tools: '工具', Top: '置顶', TopLeft: '左上', TopRight: '右上',
  TrendCharts: '趋势图', Trophy: '奖杯', TrophyBase: '奖杯底座', TurnOff: '关闭', Umbrella: '雨伞', Unlock: '解锁', Upload: '上传', UploadFilled: '上传实心', User: '用户', UserFilled: '用户实心', Van: '货车', VideoCamera: '摄像机', VideoCameraFilled: '摄像机实心',
  VideoPause: '视频暂停', VideoPlay: '视频播放', View: '查看', Wallet: '钱包', WalletFilled: '钱包实心', Warning: '警告', WarningFilled: '警告实心', Watch: '手表', Watermelon: '西瓜', WindPower: '风力', WrenchTool: '扳手', ZoomIn: '放大', ZoomOut: '缩小'
}

function getIconLabel(name: string): string {
  return iconNameMap[name] || name
}

const allIcons = Object.keys(ElementPlusIconsVue).filter(name => name !== 'default')

const filteredIcons = computed(() => {
  if (!searchText.value) return allIcons
  const keyword = searchText.value.toLowerCase()
  return allIcons.filter(name => {
    const label = getIconLabel(name)
    return label.includes(keyword) || name.toLowerCase().includes(keyword)
  })
})

const handleSelect = (icon: string) => {
  emit('update:modelValue', icon)
  popoverVisible.value = false
}

const handleClear = () => {
  emit('update:modelValue', '')
}
</script>

<style lang="scss" scoped>
.icon-select {
  width: 100%;
}

.icon-grid {
  display: grid;
  grid-template-columns: repeat(6, 1fr);
  gap: 6px;
}

.icon-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 8px 4px;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  cursor: pointer;
  transition: all 0.2s;

  &:hover {
    border-color: #409eff;
    color: #409eff;
    background: #ecf5ff;
  }

  &.is-active {
    border-color: #409eff;
    color: #409eff;
    background: #ecf5ff;
  }

  .icon-name {
    font-size: 10px;
    margin-top: 4px;
    text-align: center;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
    max-width: 70px;
  }
}
</style>
