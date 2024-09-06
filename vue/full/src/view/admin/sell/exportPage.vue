<template>
  <div id="order-form" class="order-form">
    <table>
      <tr>
        <td colspan="3">客户名称: {{ customerName }}</td>
        <td colspan="3">送货日期: {{ deliveryDate }}</td>
      </tr>
      <tr>
        <td colspan="3">联系人: {{ contactPerson }}</td>
        <td colspan="3">联系电话: {{ contactPhone }}</td>
      </tr>
      <tr>
        <th>序号</th>
        <th>物品名称</th>
        <th>规格型号</th>
        <th>数量</th>
        <th>单价（含13%税）</th>
        <th>备注</th>
      </tr>
      <tr v-for="(item, index) in items" :key="index">
        <td>{{ index + 1 }}</td>
        <td>{{ item.name }}</td>
        <td>{{ item.spec }}</td>
        <td>{{ item.quantity }}</td>
        <td>{{ item.price }}</td>
        <td>{{ item.remark }}</td>
      </tr>
    </table>
    <div class="payment-terms">
      <p>1、付款方式: {{ paymentMethod }}</p>
      <p>2、交货日期: {{ deliveryTerm }}</p>
    </div>
    <div class="signatures">
      <div class="signature-column">
        <div>{{ companyName1 }}（签章）</div>
        <div>{{ signDate1 }}</div>
      </div>
      <div class="stamp">
        <img src="/static/img/logo.png" alt="公司印章" />
      </div>
      <div class="signature-column">
        <div>{{ companyName2 }}（签章）</div>
        <div>{{ signDate2 }}</div>
      </div>
    </div>
    <button @click="exportToPDF">导出PDF</button>
  </div>
</template>

<script>
import html2canvas from 'html2canvas';
import jsPDF from 'jspdf';

export default {
  data() {
    return {
      customerName: '宁波凯盛汽车零部件有限公司',
      deliveryDate: '2024年8月22日',
      contactPerson: '洪利文',
      contactPhone: '13566616787',
      items: [
        {
          name: '三瓦楞外箱',
          spec: '113.5*80*100',
          quantity: 200,
          price: 94,
          remark: '',
        },
      ],
      paymentMethod: '货到付款。（以上报价包含13%的税，包含运费）',
      deliveryTerm: '下单后5——7个工作日交货',
      companyName1: '宁波凯瑞汽车零部件有限公司',
      companyName2: '宁波洁辉包装有限公司',
      signDate1: '2024年8月22日',
      signDate2: '2024年8月22日',
    };
  },
  methods: {
    exportToPDF() {
      const iframeEL = document.querySelector('.target-el-iframe');
      if (iframeEL) {
        iframeEL.remove();
      }
      //目标元素
      const info = document.getElementById('order-form');
      //创建iframe
      const iframeEl = document.createElement('iframe');
      iframeEl.classList.add('target-el-iframe');
      iframeEl.setAttribute('style', 'width:90%');

      //添加到页面
      document.body.appendChild(iframeEl);
      const doc = iframeEl.contentWindow.document;
      doc.write('<div class="print-iframe">' + info.innerHTML + '</div>');
      doc.write('<style>@page{size:auto;margin: 0.5cm 1cm 0cm 1cm;}</style>');
      doc.close();
      //引入第三方样式文件
      const link = doc.createElement('link');
      link.rel = 'stylesheet';
      link.href = '/static/print.css';

      doc.head.appendChild(link);
      //打印
      iframeEl.onload = () => {
        iframeEl.contentWindow.print();
      };

      // const element = document.getElementById('order-form');
      // html2canvas(element, {
      //   scale: 2, // 提高渲染质量
      //   useCORS: true, // 允许加载跨域图片
      // }).then((canvas) => {
      //   const imgData = canvas.toDataURL('image/png');
      //   const pdf = new jsPDF('p', 'mm', 'a4');
      //   const pdfWidth = pdf.internal.pageSize.getWidth();
      //   const pdfHeight = (canvas.height * pdfWidth) / canvas.width;
      //   pdf.addImage(imgData, 'PNG', 0, 0, pdfWidth, pdfHeight);
      //   pdf.save('order-form.pdf');
      // });
    },
  },
};
</script>

<style scoped>
.order-form {
  font-family: SimSun, serif; /* 使用宋体 */
  max-width: 210mm; /* A4纸宽度 */
  margin: 0 auto;
  padding: 20px;
  border: 1px solid #ccc;
  box-sizing: border-box;
}

table {
  width: 100%;
  border-collapse: collapse;
}

th,
td {
  border: 1px solid #000;
  padding: 8px;
  text-align: left;
  font-size: 12pt;
}

.payment-terms {
  margin-top: 20px;
  font-size: 12pt;
}

.signatures {
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
  margin-top: 40px;
}

.signature-column {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
}

.stamp {
  position: relative;
  width: 100px;
  height: 100px;
}

.stamp img {
  width: 100%;
  height: 100%;
  object-fit: contain;
}

button {
  margin-top: 20px;
  padding: 10px 20px;
  background-color: #4caf50;
  color: white;
  border: none;
  cursor: pointer;
}

@media print {
  .order-form {
    border: none;
    padding: 0;
  }

  button {
    display: none;
  }
}
</style>
