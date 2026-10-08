// Bài cẩm nang biên tập. Lịch/kết quả giải đấu dẫn sang nguồn chính thức để tránh dữ liệu cũ.
export const articles = [
  {
    id: 'tournaments',
    category: 'GIẢI ĐẤU',
    title: 'Theo dõi các giải cầu lông lớn ở đâu?',
    image: '/images/lindan.jpg',
    summary: 'Tra cứu lịch thi đấu và cập nhật từ hệ thống giải đấu BWF.',
    paragraphs: [
      'Khi lên kế hoạch theo dõi giải đấu, hãy kiểm tra lịch trực tiếp tại BWF vì ngày thi đấu và danh sách vận động viên có thể được cập nhật.',
      'Dùng trang giải đấu chính thức để xem cấp độ giải, địa điểm và thông tin do ban tổ chức công bố. Carrot không hiển thị lịch hoặc kết quả chưa được xác minh.',
    ],
    sources: [
      { label: 'BWF – Lịch giải đấu', url: 'https://bwfbadminton.com/calendar/' },
    ],
  },
  {
    id: 'shuttles',
    category: 'CHỌN CẦU',
    title: 'Chọn cầu cho buổi tập và trận đấu',
    image: '/images/doi.jpg',
    summary: 'Xem loại cầu, quy cách và ngân sách trước khi mua.',
    paragraphs: [
      'Khi chọn cầu, hãy xác định bạn cần cầu lông vũ hay cầu tổng hợp, sau đó xem tốc độ, quy cách và hướng dẫn của nhà sản xuất.',
      'Người chơi phong trào có thể cân nhắc chi phí mỗi buổi và độ bền. Với buổi tập nghiêm túc hoặc thi đấu, ưu tiên loại cầu thống nhất với nhóm chơi và yêu cầu của giải. Không có một mẫu cầu phù hợp cho mọi người chỉ dựa vào trình độ.',
    ],
    sources: [
      {
        label: 'Yonex – Danh mục cầu lông',
        url: 'https://www.yonex.com/badminton/shuttlecocks',
      },
    ],
  },
  {
    id: 'racquets',
    category: 'CHỌN VỢT',
    title: 'Tìm cây vợt hợp cách chơi của bạn',
    image: '/images/thuylin.jpg',
    summary: 'Tham khảo công cụ chọn vợt, rồi thử cảm giác thực tế.',
    paragraphs: [
      'Tham khảo công cụ chọn vợt chính thức để thu hẹp lựa chọn. Sau đó so sánh thông số, trọng lượng và cảm giác cầm của các mẫu phù hợp.',
      'Trước khi dùng, kiểm tra khung, thân và tay cầm. Vợt có dấu hiệu hư hỏng cần được kiểm tra trước khi tiếp tục chơi.',
    ],
    sources: [
      {
        label: 'Yonex – Racquet Selector',
        url: 'https://www.yonex.com/badminton/racquetselector',
      },
      { label: 'Yonex – An toàn sản phẩm', url: 'https://www.yonex.com/product-safety' },
    ],
  },
  {
    id: 'shoes',
    category: 'CHỌN GIÀY',
    title: 'Đừng chọn giày chỉ qua ngoại hình',
    image: '/images/anse.jpg',
    summary: 'Độ vừa chân và cảm giác khi di chuyển cũng cần được kiểm tra.',
    paragraphs: [
      'Khi thử giày, chú ý độ vừa ở mũi, gót và bề ngang. Các mẫu có cấu trúc và bề rộng khác nhau có thể mang lại cảm giác khác nhau.',
      'Tham khảo thông tin của nhà sản xuất, thử trực tiếp và tuân theo hướng dẫn sử dụng. Không suy ra một đôi giày phù hợp chỉ từ hình ảnh hoặc giá bán.',
    ],
    sources: [
      { label: 'Yonex – An toàn sản phẩm', url: 'https://www.yonex.com/product-safety' },
    ],
  },
]
