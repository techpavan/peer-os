$(document).ready(function() {
	$('.b-nav-menu-link').on('click', function(){
		if($(this).next('.b-nav-menu__sub').length > 0) {
			if($(this).parent().hasClass('b-nav-menu_active')) {
				$(this).parent().removeClass('b-nav-menu_active');
				$(this).next('.b-nav-menu__sub').slideUp(300);
			} else {
				$(this).parent().addClass('b-nav-menu_active');
				$(this).next('.b-nav-menu__sub').slideDown(300);
			}
			return false;
		}
	});

	function colEqualHeight() {
		if( $('.b-nav').height() > $('.b-workspace').height() ) {
			$('.b-workspace').height( $('.b-nav').height() );
		}else if( $('.b-nav').height() < $('.b-workspace').height() ) {
			$('.b-nav').height( $('.b-workspace').height() );
		}
	}
	colEqualHeight();
});

$(".b-form-input_dropdown").click(function () {
	$(this).toggleClass("is-active");
});

$(".b-form-input-dropdown-list").click(function(e) {
	e.stopPropagation();
});
$(".b-hub-status__btn").click(function () {
	$(".b-hub-status__dropdown").toggleClass("show");
});

$('.js-scrollbar').perfectScrollbar();

var UPDATE_NIGHTLY_BUILD_STATUS;

//document.getElementById("uploadBtn").onchange = function () {
//	document.getElementById("uploadFile").value = this.value;
//};
